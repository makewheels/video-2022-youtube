package com.github.makewheels.video2022youtube;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.comm.Protocol;
import com.aliyun.oss.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AliyunOssService {

    public OSS getStsClient(String endpoint, String accessKeyId, String secretKey, String sessionToken) {
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setProtocol(Protocol.HTTPS);
        return new OSSClientBuilder().build(endpoint, accessKeyId, secretKey, sessionToken, configuration);
    }

    public void upload(File file, JSONObject credentials) {
        String bucket = credentials.getString("bucket");
        String endpoint = credentials.getString("endpoint");
        String accessKeyId = credentials.getString("accessKeyId");
        String secretKey = credentials.getString("secretKey");
        String sessionToken = credentials.getString("sessionToken");
        String key = credentials.getString("key");

        log.info("开始上传对象存储，key = " + key);

        OSS client = getStsClient(endpoint, accessKeyId, secretKey, sessionToken);

        // 创建InitiateMultipartUploadRequest对象。
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucket, key);
        // 初始化分片。
        InitiateMultipartUploadResult uploadResult = client.initiateMultipartUpload(request);
        // 返回uploadId，它是分片上传事件的唯一标识。您可以根据该uploadId发起相关的操作，例如取消分片上传、查询分片上传等。
        String uploadId = uploadResult.getUploadId();

        // 每个分片的大小，用于计算文件有多少个分片。单位为字节。
        long partSize = 1024 * 1024L;

        long fileLength = file.length();
        int partCount = (int) (fileLength / partSize);
        if (fileLength % partSize != 0) {
            partCount++;
        }
        System.out.println(partCount);
        // partETags是PartETag的集合。PartETag由分片的ETag和分片号组成。
        List<PartETag> partETags = new ArrayList<>(partCount);
        for (int i = 0; i < partCount; i++) {
            partETags.add(null);
        }
        // 遍历分片上传。
        for (int i = 0; i < partCount; i++) {
            long startPosition = i * partSize;
            long currentPartSize = (i + 1 == partCount) ? (fileLength - startPosition) : partSize;
            InputStream inputStream = null;
            try {
                inputStream = new FileInputStream(file);
                // 跳过已经上传的分片。
                inputStream.skip(startPosition);
            } catch (IOException e) {
                e.printStackTrace();
            }
            UploadPartRequest uploadPartRequest = new UploadPartRequest(bucket, key);
            uploadPartRequest.setUploadId(uploadId);
            uploadPartRequest.setInputStream(inputStream);
            // 设置分片大小。除了最后一个分片没有大小限制，其他的分片最小为100 KB。
            uploadPartRequest.setPartSize(currentPartSize);
            // 设置分片号。每一个上传的分片都有一个分片号，取值范围是1~10000，如果超出此范围，OSS将返回InvalidArgument错误码。
            uploadPartRequest.setPartNumber(i + 1);
            // 每个分片不需要按顺序上传，甚至可以在不同客户端上传，OSS会按照分片号排序组成完整的文件。
            UploadPartResult uploadPartResult = client.uploadPart(uploadPartRequest);
            // 每次上传分片之后，OSS的返回结果包含PartETag。PartETag将被保存在partETags中。
            PartETag partETag = uploadPartResult.getPartETag();
            log.info("partNumber = " + partETag.getPartNumber() + ", partETag = " + JSON.toJSONString(partETag));
            partETags.set(i, partETag);
//            partETags.add(uploadPartResult.getPartETag());
        }

        // 创建CompleteMultipartUploadRequest对象。
        // 在执行完成分片上传操作时，需要提供所有有效的partETags。
        // OSS收到提交的partETags后，会逐一验证每个分片的有效性。
        // 当所有的数据分片验证通过后，OSS将把这些分片组合成一个完整的文件。
        CompleteMultipartUploadRequest completeMultipartUploadRequest =
                new CompleteMultipartUploadRequest(bucket, key, uploadId, partETags);

        // 完成分片上传。
        CompleteMultipartUploadResult completeMultipartUploadResult
                = client.completeMultipartUpload(completeMultipartUploadRequest);
        log.info(completeMultipartUploadResult.getETag());

        log.info("上传对象存储完成");
    }

    public static void main(String[] args) {
        JSONObject jsonObject = JSONObject.parseObject("{\"bucket\":\"video-2022-dev\",\"accessKeyId\":" +
                "\"STS.NStCwvm5iTDtqNc2M5hb84GQA\",\"endpoint\":\"oss-cn-beijing.aliyuncs.com\"" +
                ",\"secretKey\":\"3CpvSmDCMU3dW57eHAe7RhKdGfET6ALY9EyQywnv8HkT\",\"provider\":" +
                "\"ALIYUN\",\"sessionToken\":\"CAISogJ1q6Ft5B2yfSjIr5DBCM3CgOpI44afc2jS1k1gZO0U24L" +
                "6ozz2IH9LeHVhB+4WsPQ0lW1U6vwdlplpTJtIfkHfdsp36LJe9A7kbtud4pe44OwO0Mb7RTnDVU+qjZ" +
                "aPaujyQo2GcPr8OgicIovnaVKiJ1uYRFWAHcCjq/wON6Y6PGSRaT5BG60lRG9Lo9MbMn38LOukNgWQ" +
                "7EPbEEtvvHgX6wo9k9PdpPeR8R3Dllb35/YIroDqWPieYtJrIY10XqXevoU0VNKYjncAtEgWrvcu3PMY" +
                "p2qXhLzHXQkNuSfhGvHP79hiIDV+YqUHAKNepJD+76Yn5bCPxtqpm0gdZrAID3iFG5rb2szAFaau" +
                "Lc18a7f3NnOIiIjVbIk/RvX84JKDXhqAAU2V/apWGLZbEt7z4/MGKgivHVUSi7zOfhSipmeGvgf+" +
                "+2JEkYz/4y53geItQZ3QcQRUpz3s1M6yrF5IyFHugG0yYAC+sKMTOk+yvQoJGSWZVCCDEXcJrCk6NE" +
                "zyKwJ8ODTVN/4UVfaAggujuN1pipd14AFhdNhhRpb9Kgv1UPPm\",\"expiration\":\"" +
                "2022-04-11T10:15:36Z\",\"key\":\"videos/6231de9a5bffa00422da71ce/" +
                "6253d4a3e6181b5c6c3d8cfb/original/6253d4a3e6181b5c6c3d8cfb.webm\"}");

        new AliyunOssService().upload(new File("C:\\Users\\thedoflin\\Downloads\\test.mp4"),
                jsonObject);
    }

}