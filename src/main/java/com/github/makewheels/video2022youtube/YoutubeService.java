package com.github.makewheels.video2022youtube;

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
        String downloadCmd = "yt-dlp -S height:1080 -o " + file.getAbsolutePath() + " " + youtubeVideoId;
        log.info("downloadCmd = " + downloadCmd);
        executeAndPrint(downloadCmd);

        //调国内服务器接口，获取上传凭证
        String uploadCredentialsJson = HttpUtil.get(body.getString("getUploadCredentialsUrl"));
        log.info("上传凭证：" + uploadCredentialsJson);
        JSONObject uploadCredentials = JSONObject.parseObject(uploadCredentialsJson);

        //判断provider，上传到对象存储
        if (provider.equals(Provider.ALIYUN)) {
            aliyunOssService.upload(file, uploadCredentials.getJSONObject("data"));
        }

        log.info("回调通知国内服务器，文件上传完成：" + body.getString("fileUploadFinishCallbackUrl"));
        log.info(HttpUtil.get(body.getString("fileUploadFinishCallbackUrl")));

        log.info("回调通知国内服务器，视频源文件上传完成：" + body.getString(
                "videoOriginalFileUploadFinishCallbackUrl"));
        log.info(HttpUtil.get(body.getString("videoOriginalFileUploadFinishCallbackUrl")));

        //删除本地文件
//        webmFile.delete();
//        webmFile.getParentFile().delete();

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
