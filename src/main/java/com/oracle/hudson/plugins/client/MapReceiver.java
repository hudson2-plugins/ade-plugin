package com.oracle.hudson.plugins.client;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MapMessage;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQConnection;

public class MapReceiver {

	public static void main(String[] args) throws Exception {
		Connection connection = new AMQConnection(
				"amqp://guest:guest@test/?brokerlist='tcp://slc01qhl.us.oracle.com:5672'");

		connection.start();

		Session session = connection.createSession(false,
				Session.AUTO_ACKNOWLEDGE);
		Destination queue = new AMQAnyDestination(
				"ADDR:message_queue; {create: always}");
		MessageConsumer consumer = session.createConsumer(queue);

		MapMessage m = (MapMessage) consumer.receive();
		System.out.println(m);
		connection.close();
	}
}
