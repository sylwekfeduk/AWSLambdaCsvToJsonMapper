package pl.fis.lbd.aws;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import java.io.*;

public class SQSHandler implements RequestHandler<SQSEvent, Void> {

    public final String AWS_REGION = "eu-central-1";
    public final String S3_ENDPOINT = "http://localstack:4566";

    @Override
    public Void handleRequest(SQSEvent sqsEvent, Context context) {

        String bucket = "outputdata";
        String key = "fileFromJsonToCsv";

        try {
            JsonNode jsonTree = new ObjectMapper().readTree(sqsEvent.getRecords().get(0).getBody());
            CsvSchema.Builder csvSchemaBuilder = CsvSchema.builder();
            JsonNode obj = jsonTree.elements().next();
            obj.fieldNames().forEachRemaining(fieldName -> {csvSchemaBuilder.addColumn(fieldName);});
            CsvSchema csvSchema = csvSchemaBuilder.build().withHeader().withColumnSeparator((char) 59);
            CsvMapper csvMapper = new CsvMapper();
            String data = csvMapper.writerFor(JsonNode.class)
                    .with(csvSchema)
                    .writeValueAsString(jsonTree);
            prepareS3().putObject(new PutObjectRequest(bucket, key, new ByteArrayInputStream(data.getBytes()), new ObjectMetadata()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
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
}
