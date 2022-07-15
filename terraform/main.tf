terraform {
  required_providers {
    aws = {
      source = "hashicorp/aws"
      version = "~> 3.49"
    }
  }

  required_version = ">=0.14.9"
}

provider "aws" {
  profile = "local"
  region = var.aws_region
  access_key = "foo"
  s3_force_path_style = true
  secret_key = "bar"
  skip_credentials_validation = true
  skip_metadata_api_check = true
  skip_requesting_account_id = true

  endpoints {
    sqs = "http://localhost:4566"
    s3 = "http://localhost:4566"
    lambda = "http://localhost:4566"
  }
}

variable "aws_region" {
  type = string
}

resource "aws_s3_bucket" "bucketWithInputData" {
  bucket = "inputdata"
}

resource "aws_s3_bucket" "bucketWithOutputData" {
  bucket = "outputdata"
}

resource "aws_sqs_queue" "queueWithJsonFiles" {
  name = "myQueue"
  visibility_timeout_seconds = 30
  message_retention_seconds = 86400
}

resource "aws_lambda_function" "lambdaMappingCsvToJson" {
  function_name = "mapperCsvToJson"
  filename = "C:\\Users\\sfeduk\\Downloads\\AWS\\AWSLotto\\target\\AWSLotto-1.0-SNAPSHOT.jar"
  source_code_hash = "C:\\Users\\sfeduk\\Downloads\\AWS\\AWSLotto\\target\\AWSLotto-1.0-SNAPSHOT.jar"
  runtime = "java11"
  handler = "pl.fis.lbd.aws.BucketHandler"
  role = "arn:aws:iam::12345:role/ignoreme"
  memory_size = 512
  timeout = 5

  environment {
    variables = {
      RUN_PROFILE = "local"
    }
  }
}

resource "aws_lambda_function" "lambdaMappingJsonToCsv" {
  function_name = "mapperJsonToCsv"
  filename          = "C:\\Users\\sfeduk\\Downloads\\AWS\\AWSLotto\\target\\AWSLotto-1.0-SNAPSHOT.jar"
  source_code_hash = "C:\\Users\\sfeduk\\Downloads\\AWS\\AWSLotto\\target\\AWSLotto-1.0-SNAPSHOT.jar"
  runtime = "java11"
  handler = "pl.fis.lbd.aws.SQSHandler"
  role = "arn:aws:iam::12345:role/ignoreme"
  memory_size = 512
  timeout = 5

  environment {
    variables = {
      RUN_PROFILE = "local"
    }
  }
}

resource "aws_s3_bucket_notification" "lambdaCsvToJsonS3Notification" {
  bucket = aws_s3_bucket.bucketWithInputData.id

  lambda_function {
    lambda_function_arn = aws_lambda_function.lambdaMappingCsvToJson.arn
    events = ["s3:ObjectCreated:*"]
  }
}

resource "aws_lambda_event_source_mapping" "lambdaJsonToCsvSQSEventSource" {
  function_name = aws_lambda_function.lambdaMappingJsonToCsv.function_name
  batch_size = 5
  maximum_batching_window_in_seconds = 60
  event_source_arn = aws_sqs_queue.queueWithJsonFiles.arn
}