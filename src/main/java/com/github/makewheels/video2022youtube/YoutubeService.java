package com.github.makewheels.video2022youtube;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RuntimeUtil;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;

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

    public void download(String youtubeUrl) {
        new Thread(() -> {
            //获取视频文件信息
            String getFilenameCmd = "yt-dlp --get-filename -o '%(title)s.%(ext)s' "
                    + "--restrict-filenames " + youtubeUrl;
            log.info("getFilenameCmd = " + getFilenameCmd);
            String filename = RuntimeUtil.execForStr(getFilenameCmd);
            log.info("filename before = " + filename);
            filename = filename.replace("'", "");
            filename = filename.replace("+", "_");
            filename = filename.replace("%", "_");
            filename = filename.replace("#", "_");
            filename = filename.replace("&", "_");
            filename = filename.replace("\"", "_");
            filename = filename.replace("<", "_");
            filename = filename.replace(">", "_");
            filename = filename.replace("~", "_");
            filename = filename.replace("^", "_");
            filename = filename.replace("!", "_");
            filename = filename.replace("@", "_");
            filename = filename.replace("$", "_");
            filename = filename.replace("*", "_");
            filename = filename.replace("`", "_");
            filename = filename.replace("(", "_");
            filename = filename.replace(")", "_");
            filename = filename.replace(" ", "_");
            filename = filename.replace("\n", "");
            filename = filename.replace("\r", "");
            log.info("filename after = " + filename);

            String workId = IdUtil.randomUUID();
            //下载视频
            File webmFile = new File(youtubeWorkDir, workId + "/" + filename);
            log.info("webmFile = " + webmFile);
            String downloadCmd = "yt-dlp -S height:1080 -o " + webmFile.getAbsolutePath() + " " + youtubeUrl;
            log.info("downloadCmd = " + downloadCmd);
            executeAndPrint(downloadCmd);

            //上传对象存储
            log.info("开始上传对象存储");
            long start = System.currentTimeMillis();

//            String base = BaiduCloudUtil.getObjectStoragePrefix(videoId);
//            String key = base + videoId + "." + FilenameUtils.getExtension(webmFile.getName());
//            log.info("key = " + key);
//            String playFileUrl = BaiduCloudUtil.uploadObjectStorage(webmFile, key);

            log.info("上传对象存储完成");
//            log.info("playFileUrl = " + playFileUrl);
            long end = System.currentTimeMillis();
            long time = end - start;
            long fileLength = webmFile.length();
            long speedPerSecond = fileLength / time * 1000;
            String readable = FileUtil.readableFileSize(speedPerSecond);
            log.info("上传对象存储速度:  " + readable + "/s");

            //通知
            log.info("通知国内服务器");

            //删除本地下载的文件
//            log.info("FileUtil.del(new File(workDir, videoId)) = "
//                    + FileUtil.del(new File(workDir, videoId)));

        }).start();
    }

    /**
     * 提交新视频任务
     */
    public JSONObject submitMission(String youtubeUrl) {
        download(youtubeUrl);

        //提前先返回播放地址
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("youtubeUrl", youtubeUrl);
        return jsonObject;
    }

}
