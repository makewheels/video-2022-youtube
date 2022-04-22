package com.github.makewheels.video2022youtube;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class YoutubeService {
    @Value("${youtube-work-dir}")
    private String youtubeWorkDir;

    @Resource
    private AliyunOssService aliyunOssService;

    public void executeAndPrint(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            InputStream inputStream = process.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                log.info(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 根据视频id获取下载文件后缀
     *
     * @param youtubeVideoId
     * @return
     */
    public String getFileExtension(String youtubeVideoId) {
        log.info("获取后缀：youtubeVideoId = " + youtubeVideoId);
        String getFilenameCmd = "yt-dlp --get-filename -o %(ext)s " + "--restrict-filenames "
                + youtubeVideoId;
        log.info("getFilenameCmd = " + getFilenameCmd);
        String result = RuntimeUtil.execForStr(getFilenameCmd);
        log.info("得到了结果：" + result);
        if (result.endsWith("\n")) {
            result = result.replace("\n", "");
        }
        return result;
    }

    /**
     * 下载
     */
    private void download(JSONObject body) {
        String missionId = body.getString("missionId");
        String provider = body.getString("provider");
        String videoId = body.getString("videoId");
        String youtubeVideoId = body.getString("youtubeVideoId");
        String key = body.getString("key");
        //拿文件拓展名
        String extension = FileNameUtil.extName(key);
        //下载视频
        File file = new File(youtubeWorkDir, missionId + "-" + videoId + "/"
                + youtubeVideoId + "." + extension);
        log.info("webmFile = " + file.getAbsolutePath());
        String downloadCmd = "yt-dlp -S height:720 -o " + file.getAbsolutePath() + " " + youtubeVideoId;
        log.info("downloadCmd = " + downloadCmd);
        executeAndPrint(downloadCmd);
        log.info("下载完成 " + file.getName());

        //2022年4月15日12:35:43
        //这里发现个问题，yt-dlp获取后缀永远是webm，但是实际文件后缀可能是mkv，这会导致上传的时候找不到文件
        //问题具体如下：
        //
        //有问题的下载命令：
        //yt-dlp -S height:1080 -o /root/youtube-work-dir/jV5tCkJGG-4BWPEWH9g-6-6258f1671273947edf88553c/
        // 1otF0N6surM.webm 1otF0N6surM
        //
        //海外服务器文件如下：
        //[root@VM6oN0mVD94k jV5tCkJGG-4BWPEWH9g-6-6258f1671273947edf88553c]# ll
        //总用量 413020
        //-rw-r--r-- 1 root root 422932253 3月   5 04:17 1otF0N6surM.mkv
        //
        //咋办呢，本质上是，对象存储上传后缀不对，但是先不解决后缀不对的问题，先让他能正常上传，
        // 这里先判断一下，file存不存在，如果存在继续上传对象存储，
        //如果file.exist()==false则把file改为mkv文件，具体代码就是该目录第一个文件
        if (!file.exists()) {
            file = FileUtil.loopFiles(file.getParentFile()).get(0);
        }

        log.info("待上传的本地文件：" + file.getAbsolutePath());

        //调国内服务器接口，获取上传凭证
        String uploadCredentialsJson = HttpUtil.get(body.getString("getUploadCredentialsUrl"));
        log.info("获取到上传凭证：" + uploadCredentialsJson);
        JSONObject uploadCredentials = JSONObject.parseObject(uploadCredentialsJson);

        //判断provider，上传到对象存储
        if (provider.startsWith(S3Provider.ALIYUN)) {
            aliyunOssService.upload(file, uploadCredentials.getJSONObject("data"));
        }

        log.info("回调通知国内服务器，文件上传完成：" + body.getString("fileUploadFinishCallbackUrl"));
        log.info(HttpUtil.get(body.getString("fileUploadFinishCallbackUrl")));

        log.info("回调通知国内服务器，视频源文件上传完成：" + body.getString(
                "videoOriginalFileUploadFinishCallbackUrl"));
        log.info(HttpUtil.get(body.getString("videoOriginalFileUploadFinishCallbackUrl")));

        //删除本地文件
        file.delete();
        file.getParentFile().delete();

    }

    /**
     * 提交搬运任务
     */
    public JSONObject submitMission(JSONObject body) {
        log.info("收到搬运任务：" + body.toJSONString());
        String missionId = body.getString("missionId");
        String videoId = body.getString("videoId");
        String youtubeVideoId = body.getString("youtubeVideoId");
        //开始下载
        log.info("开始下载: youtubeVideoId = " + youtubeVideoId);
        new Thread(() -> download(body)).start();
        //提前先返回播放地址
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("missionId", missionId);
        jsonObject.put("videoId", videoId);
        jsonObject.put("youtubeVideoId", youtubeVideoId);
        jsonObject.put("message", "我是海外服务器，已收到搬运YouTube任务");
        return jsonObject;
    }

    private YouTube getService() {
        NetHttpTransport httpTransport = null;
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
        if (httpTransport == null) return null;
        return new YouTube.Builder(httpTransport, JacksonFactory.getDefaultInstance(), null)
                .setApplicationName("API code samples")
                .build();
    }

    /**
     * 获取视频信息
     *
     * @param youtubeVideoId
     * @return
     */
    public JSONObject getVideoInfo(String youtubeVideoId) {
        List<String> idList = new ArrayList<>();
        idList.add(youtubeVideoId);
        YouTube youTube = getService();
        if (youTube == null) return null;
        try {
            YouTube.Videos.List request = youTube.videos()
                    .list(Lists.newArrayList("snippet", "contentDetails", "statistics"))
                    .setId(idList).setKey("AIzaSyA4x7iV1uzQqWnoiADcHikWshx01tvZEtg");
            VideoListResponse response = request.execute();
            return JSONObject.parseObject(JSON.toJSONString(response.getItems().get(0)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
