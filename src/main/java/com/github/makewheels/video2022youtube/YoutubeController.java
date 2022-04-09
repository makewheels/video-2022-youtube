package com.github.makewheels.video2022youtube;

import com.alibaba.fastjson.JSONObject;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("youtube")
public class YoutubeController {
    @Resource
    private YoutubeService youtubeService;

    /**
     * 提交新视频
     */
    @PostMapping("submitMission")
    public JSONObject submitMission(@RequestParam("youtubeUrl") String youtubeUrl) {
        return youtubeService.submitMission(youtubeUrl);
    }
}
