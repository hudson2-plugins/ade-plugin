package com.oracle.hudson.plugins;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.TaskListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.model.listeners.RunListener;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Session;

import net.sf.json.JSONObject;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQAnyDestination;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.url.URLSyntaxException;
import org.kohsuke.stapler.StaplerRequest;

import com.thoughtworks.xstream.annotations.XStreamOmitField;

@Extension
public class MessageProducer extends RunListener<Run> implements Describable<MessageProducer> {
	
	@Override
	public void onCompleted(Run r, TaskListener listener) {
		publish(new JobMessageCallback(r,"onCompleted"));
	}
	@Override
	public void onDeleted(Run r) {
		publish(new JobMessageCallback(r,"onDeleted"));
	}
	@Override
	public void onFinalized(Run r) {
		publish(new JobMessageCallback(r,"onFinalized"));
	}
	@Override
	public void onStarted(Run r, TaskListener listener) {
		publish(new JobMessageCallback(r,"onStarted"));
	}
	public Descriptor<MessageProducer> getDescriptor() {
		return Hudson.getInstance().getDescriptorOrDie(getClass());
	}
	
	private void publish(MessageCallback callback) {
		Boolean b = ((MessageProducerDescriptor)getDescriptor()).getEnabled();
		if (b) {
			((MessageProducerDescriptor)getDescriptor()).publish(callback);
		} else {
			System.out.println("skipping event processing because the listener is disabled");
		}
	}
	
	private void createEvent(Run r,String name, MapMessage m) throws JMSException {
		m.setString("hudson.rootUrl", Hudson.getInstance().getRootUrl());
		m.setString("project.name", r.getParent().getName());
		if (r.getResult()!=null) {
			m.setString("job.status.name",r.getResult().toString());
			m.setInt("job.status.ordinal",r.getResult().getOrdinal());
		}
		m.setInt("job.number", r.getNumber());
		m.setString("job.fullDisplayName",r.getFullDisplayName());
		m.setString("job.fullName", r.getFullName());
		m.setString("event", name);
		// note: a job records it's "cause" which is a description of why it was started.  This might be useful
		// for doing correlation
	}
	
	class JobMessageCallback implements MessageCallback {
		private Run run;
		private String message;
		JobMessageCallback(Run r,String m) {
			this.run = r;
			this.message = m;
		}
		public void populate(MapMessage m) throws JMSException {
			createEvent(run,message,m);
		}
	}
	
	interface MessageCallback {
		void populate(MapMessage m) throws JMSException;
	}

	@Extension
	public static class MessageProducerDescriptor extends Descriptor<MessageProducer> {
		private Boolean isEnabled = false;
		private String uri = "amqp://guest:guest@/test?brokerlist='tcp://slc01qhl.us.oracle.com:5672'";
		private String destination = "ADDR:message_queue; {create: always}";
		@XStreamOmitField
		private Connection connection;
		@XStreamOmitField
		private Session session;
		@XStreamOmitField
		private javax.jms.MessageProducer producer;
		
		public MessageProducerDescriptor() {
			load();
		}
		@Override
		public String getDisplayName() {
			return "MessageProducer";
		}
		public Boolean getEnabled() {
			return isEnabled;
		}
		public void setEnabled(Boolean b) {
			this.isEnabled = b;
		}
		public void publish(MessageCallback callback) {
			try {
				initProducer();
				MapMessage m = session.createMapMessage();
				callback.populate(m);
				producer.send(m);
			} catch (Exception e) {
				closeAllJmsObjects(e);
			}
		}
		private void closeAllJmsObjects(Exception cause) {
			System.out.println("shutting down JMS session because of:  "+cause);
			cause.printStackTrace();
			close(producer);
			close(session);
			close(connection);
		}
		private void close(Object object) {
			if (object==null) {
				return;
			}
			try {
				Method m = object.getClass().getMethod("close", new Class[]{});
				m.invoke(object, new Object[]{});
			} catch (Exception e) {
				System.err.println("error calling close method on "+object+":  "+e);
			}
		}
		void initProducer() throws IOException, JMSException, URISyntaxException, AMQException {
			synchronized(this) {
				if (producer==null) {
					connection = new AMQConnection(uri);
					session = connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
					Destination queue = new AMQAnyDestination(destination);
					producer = session.createProducer(queue);
				}
			}
		}
		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws hudson.model.Descriptor.FormException {
			if ("on".equals(req.getParameter("ade_classic.messageproducer.isEnabled"))) {
				this.isEnabled = true;
			} else {
				this.isEnabled = false;
			}
			save();
			return super.configure(req);
		}
	}
}
