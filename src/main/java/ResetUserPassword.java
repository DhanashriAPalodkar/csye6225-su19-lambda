import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;

import java.time.Instant;

public class ResetUserPassword implements RequestHandler<SNSEvent, Object>
{
    static LambdaLogger logger;

    @Override
    public Object handleRequest(SNSEvent input, Context context)
    {
        logger=context.getLogger();
        logger.log("Reset Password Lambda Function Invoked");

        String domain = System.getenv("domain");
        String email = input.getRecords().get(0).getSNS().getMessage();
        String maskedEmail = email.replaceAll("(?<=.{3}).(?=.*@)", "*");
        logger.log("Reset password called for email : "+maskedEmail);
        DyanmoDBService dbService = new DyanmoDBService();
        String token = dbService.generateToken(email);

        if(token!=null)
            sendEmail(email,domain,token);

        logger.log("Reset Password Lambda Function Completed");
        return null;
    }

    private void sendEmail(String TO, String domain, String token)
    {
        String reset_url="http://"+domain+":8080/reset?email="+TO+"&token="+token;
        String FROM = "noreply@" + domain;
        String SUBJECT = "Password Reset Requested";
        String BODY = "<h2>Password Reset</h2>"
                + "<p>Click <a href='"+reset_url+"'>"
                + "here</a> to reset you password or, copy and paste the below url in your browser <br><br>"
                + "<a href='"+reset_url+"'>" + reset_url+"</a>"
                + "<br><p>This link will expire in "+ System.getenv("TTL_MINS")+" minutes";
        String TEXTBODY = "To reset your password copy and paste the following url in your browser:\n\n "
                + reset_url+". This link will expire in "+ System.getenv("TTL_MINS")+" minute(s)";
        try
        {
            AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard()
                    .withRegion(Regions.US_EAST_1).withCredentials(new DefaultAWSCredentialsProviderChain()).build();
            SendEmailRequest request = new SendEmailRequest()
                    .withDestination(new Destination()
                            .withToAddresses(TO))
                    .withMessage(new Message()
                            .withBody(new Body()
                                    .withHtml(new Content().withCharset("UTF-8")
                                            .withData(BODY))
                                    .withText(new Content().withCharset("UTF-8")
                                            .withData(TEXTBODY)))
                            .withSubject(new Content().withCharset("UTF-8")
                                    .withData(SUBJECT)))
                    .withSource(FROM);
            SendEmailResult sendEmailResult = client.sendEmail(request);
            logger.log(sendEmailResult.toString());
            logger.log("Password reset email has been sent to " + TO.replaceAll("(?<=.{3}).(?=.*@)", "*"));
        }
        catch (Exception ex)
        {
            System.out.println("The email was not sent. Error message: " + ex.getMessage());
        }
    }
}
