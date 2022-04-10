package com.github.makewheels.video2022youtube;

import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.RuntimeUtil;
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

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class YoutubeService {
    @Value("${youtube-work-dir}")
    private String youtubeWorkDir;

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
        String getFilenameCmd = "yt-dlp --get-filename -o %(ext)s "
                + "--restrict-filenames " + youtubeVideoId;
        log.info("getFilenameCmd = " + getFilenameCmd);
        String result = RuntimeUtil.execForStr(getFilenameCmd);
        if (result.endsWith("\n")) {
            result = result.replace("\n", "");
        }
        return result;
    }

    /**
     * 下载
     *
     * @param missionId
     * @param youtubeVideoId
     * @param uploadKey
     */
    private void download(String missionId, String youtubeVideoId, String uploadKey) {
        //拿文件拓展名
        String extension = FileNameUtil.extName(uploadKey);
        //下载视频
        File webmFile = new File(youtubeWorkDir, missionId + "/" + youtubeVideoId + "." + extension);
        log.info("webmFile = " + webmFile.getAbsolutePath());
        String downloadCmd = "yt-dlp -S height:1080 -o " + webmFile.getAbsolutePath() + " " + youtubeVideoId;
        log.info("downloadCmd = " + downloadCmd);
        executeAndPrint(downloadCmd);

        log.info("开始上传对象存储");
        log.info(uploadKey);

        log.info("上传对象存储完成");

        log.info("通知国内服务器");

        //删除本地文件
        webmFile.delete();
        webmFile.getParentFile().delete();

    }

    /**
     * 提交新视频任务
     */
    public JSONObject submitMission(JSONObject body) {
        String missionId = body.getString("missionId");
        String youtubeVideoId = body.getString("youtubeVideoId");
        String uploadKey = body.getString("uploadKey");
        //开始下载
        new Thread(() -> download(missionId, youtubeVideoId, uploadKey)).start();
        //提前先返回播放地址
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("missionId", missionId);
        jsonObject.put("youtubeVideoId", youtubeVideoId);
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
