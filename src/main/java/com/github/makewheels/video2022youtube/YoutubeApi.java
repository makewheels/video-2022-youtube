package com.github.makewheels.video2022youtube;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class YoutubeApi {

    /**
     * Build and return an authorized API client service.
     *
     * @return an authorized API client service
     * @throws GeneralSecurityException, IOException
     */
    public static YouTube getService() throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        return new YouTube.Builder(httpTransport, JacksonFactory.getDefaultInstance(), null)
                .setApplicationName("API code samples")
                .build();
    }

    /**
     * Call function to create API service object. Define and
     * execute API request. Print API response.
     */
    public static void main(String[] args)
            throws GeneralSecurityException, IOException {
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", "7890");
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", "7890");
        YouTube youTube = getService();
        List<String> idList = new ArrayList<>();
        idList.add("9EBkS2kE7uk");
        YouTube.Videos.List request = youTube.videos()
                .list(Lists.newArrayList("snippet", "contentDetails", "statistics"))
                .setId(idList);
        request.setKey("AIzaSyA4x7iV1uzQqWnoiADcHikWshx01tvZEtg");
        VideoListResponse response = request.execute();
        System.out.println(response);
        Video video = response.getItems().get(0);
        VideoSnippet snippet = video.getSnippet();
        String title = snippet.getTitle();
        String description = snippet.getDescription();
        System.out.println(title);
        System.out.println(description);
    }
}
