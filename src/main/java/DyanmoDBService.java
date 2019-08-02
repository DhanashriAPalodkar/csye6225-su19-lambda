import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.UUID;

public class DyanmoDBService
{
    AmazonDynamoDB dynamoDBClient;
    DynamoDB dynamoDB = null;
    Table table;
    long TTL_MINS;

    DyanmoDBService()
    {
        try
        {
            String dynamoDBEndPoint = System.getenv("DynamoDBEndPoint");
            dynamoDBClient = AmazonDynamoDBClientBuilder.standard()
                    .withEndpointConfiguration(
                            new AwsClientBuilder.EndpointConfiguration(dynamoDBEndPoint, "us-east-1"))
                    .withCredentials(new DefaultAWSCredentialsProviderChain()).build();
            dynamoDB = new DynamoDB(dynamoDBClient);
            table = dynamoDB.getTable("csye6225");
            TTL_MINS=Long.parseLong(System.getenv("TTL_MINS"));
            ResetUserPassword.logger.log("Connected to DynamoDB successfully");
        }
        catch (Exception e)
        {
            ResetUserPassword.logger.log("Error while connecting to DynamoDB table: " + e.getMessage());
        }
    }

    public String generateToken(String email)
    {
        String token = null;
        try
        {
            QuerySpec spec = new QuerySpec()
                    .withKeyConditionExpression("email = :v_email")
                    .withFilterExpression("expiration > :v_expiration")
                    .withValueMap(new ValueMap()
                            .withString(":v_email", email)
                            .withNumber(":v_expiration", Instant.now().getEpochSecond()));
            ItemCollection<QueryOutcome> items = table.query(spec);
            Iterator<Item> iterator = items.iterator();
            while (iterator.hasNext()) {
                ResetUserPassword.logger.log(iterator.next().toJSONPretty());
            }
            if(items.getAccumulatedItemCount()==0)
            {
                token= UUID.randomUUID().toString();
                insert(email, token);
                ResetUserPassword.logger.log("New token generated ");
            }
            else
                ResetUserPassword.logger.log("Valid token exists.");
        }
        catch (Exception e)
        {
            ResetUserPassword.logger.log("Error in getToken: "+ e.getMessage());
        }
        return token;
    }

    private void insert(String email, String token)
    {
        try 
        {
            PutItemOutcome outcome = table.putItem(new Item().withPrimaryKey("email", email)
                    .withString("token", token)
                    .withNumber("expiration", Instant.now().plus(TTL_MINS, ChronoUnit.MINUTES).getEpochSecond()));
            ResetUserPassword.logger.log("Item insertion succeeded:\n" + outcome.toString());
        }
        catch (Exception e) {
            ResetUserPassword.logger.log("Error while inserting item : " + e.getMessage());
        }
    }
}
