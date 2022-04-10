package com.github.makewheels.video2022youtube;

import com.alibaba.fastjson.JSONObject;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("youtube")
public class YoutubeController {
    @Resource
    private YoutubeService youtubeService;

    @GetMapping("getFileExtension")
    public JSONObject getFileExtension(@RequestParam String youtubeVideoId) {
        String extension = youtubeService.getFileExtension(youtubeVideoId);
        JSONObject response = new JSONObject();
        response.put("extension", extension);
        return response;
    }

    @PostMapping("submitMission")
    public JSONObject submitMission(@RequestBody JSONObject body) {
        return youtubeService.submitMission(body);
    }

    @GetMapping("getVideoInfo")
    public JSONObject getVideoInfo(@RequestParam String youtubeVideoId) {
        return youtubeService.getVideoInfo(youtubeVideoId);
    }
}
