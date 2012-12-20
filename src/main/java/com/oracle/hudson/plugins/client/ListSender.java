package com.oracle.hudson.plugins.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MapMessage;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQConnection;

public class ListSender {

	public static void main(String[] args) throws Exception {
		Connection connection = new AMQConnection(
				//"amqp://jamclark:password@/?brokerlist='tcp://slc01qhl.us.oracle.com:5672'&sasl_mechs='GSSAPI'&sasl_protocol='qpidd/'&sasl_server='slc01qhl.us.oracle.com'");
				"amqp://user:password@/test?brokerlist='tcp://slc01qhl.us.oracle.com:5672'");

		Session session = connection.createSession(false,
				Session.AUTO_ACKNOWLEDGE);
		Destination queue = new AMQAnyDestination(
				"ADDR:message_queue; {create: always}");
		MessageProducer producer = session.createProducer(queue);

		MapMessage m = session.createMapMessage();
		m.setIntProperty("Id", 987654321);
		m.setStringProperty("name", "Widget");
		m.setDoubleProperty("price", 0.99);

		List<String> colors = new ArrayList<String>();
		colors.add("red");
		colors.add("green");
		colors.add("white");
		m.setObject("colours", colors);

		Map<String, Double> dimensions = new HashMap<String, Double>();
		dimensions.put("length", 10.2);
		dimensions.put("width", 5.1);
		dimensions.put("depth", 2.0);
		m.setObject("dimensions", dimensions);

		List<List<Integer>> parts = new ArrayList<List<Integer>>();
		parts.add(Arrays.asList(new Integer[] { 1, 2, 5 }));
		parts.add(Arrays.asList(new Integer[] { 8, 2, 5 }));
		m.setObject("parts", parts);

		Map<String, Object> specs = new HashMap<String, Object>();
		specs.put("colours", colors);
		specs.put("dimensions", dimensions);
		specs.put("parts", parts);
		m.setObject("specs", specs);

		producer.send(m);
		connection.close();
	}

}