package com.oracle.hudson.plugins;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Hudson;
import hudson.model.labels.LabelAtom;
import hudson.node_monitors.AbstractNodeMonitorDescriptor;
import hudson.node_monitors.NodeMonitor;
import hudson.remoting.Callable;
import hudson.slaves.OfflineCause;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Map;

import net.sf.json.JSONObject;
import oracle.sei.core.internal.utils.OkinitStrategy;

import org.kohsuke.stapler.StaplerRequest;

public class KerberosTicketMonitor extends NodeMonitor {

    @Extension
    public static final AbstractNodeMonitorDescriptor<KerberosTicket> DESCRIPTOR = new AbstractNodeMonitorDescriptor<KerberosTicket>() {
    	
    	@Override
    	protected KerberosTicket monitor(Computer c) throws IOException, InterruptedException {
    		
            KerberosTicket ticket = c.getChannel().call(new KerberosMonitorTask());
            
            if (!ticket.isValid() && c.getNode().getAssignedLabels().contains(LabelAtom.get("ade"))) {
            	try {
    				AdeViewLauncherDecorator.DescriptorImpl desc = 
    						(AdeViewLauncherDecorator.DescriptorImpl)Hudson.getInstance().getDescriptorByType(
    								AdeViewLauncherDecorator.DescriptorImpl.class);
    				if (desc==null || desc.getToken()==null || desc.getUser()==null ) {
    					throw new Exception("Master ADE config has not been configured with user/token details for kerberos");
    				}
    				String restUrlTemplate = "http://eseapi.oraclecorp.com/internal/user/{userName}?stoken="+desc.getToken();
    				c.getChannel().call(new OkinitStrategyTask(desc.getUser(),restUrlTemplate));
            	} catch (Exception e) {
            		markOffline(c, new NoKerberosTicket(e.getMessage()==null?"no message provided by OkinitStrategyTask exception":e.getMessage()));
            	}
            }
            
            return ticket;
        }

    	@Override
        public String getDisplayName() {
            return "kerberos";
        }

        @Override
        public NodeMonitor newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new KerberosTicketMonitor();
        }
    };
    
    private static class OkinitStrategyTask implements Callable<Object,Exception> {
    	private final String userName;
    	private final String restUrlTemplate;
    	
    	OkinitStrategyTask(String u, String t) {
    		this.userName = u;
    		this.restUrlTemplate = t;
    	}
		public Object call() throws Exception {
            OkinitStrategy krbStrategy = new OkinitStrategy(restUrlTemplate,userName);
            if (krbStrategy.execute()) {
                System.out.println("Kerberos Ticket Created");
            } else {
                System.err.println("Failed to create Kerberos Ticket");
                throw new Exception("Failed to create Kerberos Ticket");
            }
            // OkinitStrategy returns nothing back to the originator
            return null;
		}
    }
    
    private static class KerberosMonitorTask implements Callable<KerberosTicket,IOException> {
		private static final long serialVersionUID = 1L;
		
		public KerberosTicket call() throws IOException {
			KerberosTicket ticket = new KerberosTicket();
			
			ProcessBuilder builder = new ProcessBuilder();
			builder.command("ade","identity");
			Map<String,String> env = builder.environment();
			Process p = builder.start();
			
			try {
				p.waitFor();
				if (p.exitValue()==0) {
					System.out.println("ade identity returned error code 0:  valid ticket!");
					ticket.setValid();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = null;
			while ((line=reader.readLine())!=null) {
				System.out.println("KerberosTicketMonitorTask:("+java.net.InetAddress.getLocalHost().getHostName()+") -- "+line);
				if (line.contains("krbtgt/DEV.ORACLE.COM@DEV.ORACLE.COM")) {
					System.out.println("found a valid ticket!");
				}
			}
			
        	return ticket;
        }
    }
    
    static class NoKerberosTicket extends OfflineCause {
    	private Date date = new Date();
    	private String message;
    	NoKerberosTicket(String m) {
    		this.message = m;
    	}
		@Override
		public String toString() {
			return "node taken offline at "+date+": no valid kerberos ticket and OkinitStrategy failed("+this.message+")";
		}
    }
 }
