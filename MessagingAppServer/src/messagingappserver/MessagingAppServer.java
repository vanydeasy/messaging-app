/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package messagingappserver;

import com.rabbitmq.client.*;
import com.rabbitmq.client.AMQP.BasicProperties;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 *
 * @author vanyadeasy
 */
public class MessagingAppServer {
    private static final String QUEUE_NAME = "messaging_app";
    private DatabaseHelper dbHelper = new DatabaseHelper();
    
    public static void main(String[] argv) {
        MessagingAppServer server = new MessagingAppServer();
        Connection connection = null;
        Channel channel = null;
        JSONParser parser = new JSONParser();
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");

            connection = factory.newConnection();
            channel = connection.createChannel();

            channel.queueDeclare(QUEUE_NAME, false, false, false, null);

            channel.basicQos(1);

            QueueingConsumer consumer = new QueueingConsumer(channel);
            channel.basicConsume(QUEUE_NAME, false, consumer);

            System.out.println(" [x] Awaiting requests");

            while (true) {
                String response = null;

                QueueingConsumer.Delivery delivery = consumer.nextDelivery();

                BasicProperties props = delivery.getProperties();
                BasicProperties replyProps = new BasicProperties
                                                .Builder()
                                                .correlationId(props.getCorrelationId())
                                                .build();

                try {
                    JSONObject message = (JSONObject)parser.parse(new String(delivery.getBody(),"UTF-8"));
                    response = server.doCommand(message).toJSONString();
                }
                catch (Exception e){
                    System.out.println(" [.] " + e.toString());
                    response = "";
                }
                finally {
                    channel.basicPublish( "", props.getReplyTo(), replyProps, response.getBytes("UTF-8"));

                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignore) {}
            }
        }
    }
    
    public JSONObject doCommand(JSONObject request) {
        JSONObject response = new JSONObject();
        response.put("command", request.get("command"));
        String command = request.get("command").toString();
        
        System.out.println(">> Command: "+command);
        switch(command) {
            case "signup":
                String username = request.get("username").toString();
                String password = request.get("password").toString();
                if(dbHelper.insertUser(username,password)) {
                    response.put("status", true);
                    response.put("message", "Signup has been successful.");
                }
                else {
                    response.put("status", false);
                    response.put("message", "Username already exists.");
                }
                break;
            default:
                break;
        }
        
        return response;
    }
}
