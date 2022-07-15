package pl.fis.lbd.aws;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import java.io.*;
import java.util.*;


public class BucketHandler implements RequestHandler<S3Event, String> {

    public final String AWS_REGION = "eu-central-1";
    public final String S3_ENDPOINT = "http://localstack:4566";
    public final String S3_QUEUE = "http://localstack:4566/000000000000/myQueue";

    public String handleRequest(S3Event event, Context context) {
        S3EventNotification.S3EventNotificationRecord record = event.getRecords().get(0);
        String bucket = record.getS3().getBucket().getName();
        String key = record.getS3().getObject().getKey();

        S3Object obj = prepareS3().getObject(new GetObjectRequest(bucket, key));

        try (InputStreamReader in = new InputStreamReader(obj.getObjectContent())){

            CsvSchema csvSchema = CsvSchema.emptySchema().withHeader().withColumnSeparator((char) 59);
            CsvMapper csvMapper = new CsvMapper();
            MappingIterator<Map<?,?>> mappingIterator = csvMapper.reader().forType(Map.class).with(csvSchema).readValues(in);
            List<Map<?, ?>> list = mappingIterator.readAll();
            ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

            SendMessageRequest sendMessageRequest = new SendMessageRequest();
            sendMessageRequest.withQueueUrl(S3_QUEUE).withMessageBody(objectMapper.writeValueAsString(list)).setDelaySeconds(10);
            prepareSQS().sendMessage(sendMessageRequest);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return obj.getObjectMetadata().getContentType();
    }

    private AmazonS3 prepareS3() {
        BasicAWSCredentials credentials = new BasicAWSCredentials("foo", "bar" );

        AwsClientBuilder.EndpointConfiguration config = new AwsClientBuilder.EndpointConfiguration(S3_ENDPOINT, AWS_REGION);

        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
        builder.withEndpointConfiguration(config);
        builder.withPathStyleAccessEnabled(true);
        builder.withCredentials(new AWSStaticCredentialsProvider(credentials));
        return builder.build();
    }

    private AmazonSQS prepareSQS() {
        BasicAWSCredentials credentials = new BasicAWSCredentials("foo", "bar" );

        AwsClientBuilder.EndpointConfiguration config = new AwsClientBuilder.EndpointConfiguration(S3_QUEUE, AWS_REGION);

        AmazonSQS sqs = AmazonSQSClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withEndpointConfiguration(config)
                .build();
        return sqs;
    }
}
