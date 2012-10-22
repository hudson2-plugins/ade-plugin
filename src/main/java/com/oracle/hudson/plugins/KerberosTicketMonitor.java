package com.oracle.hudson.plugins;

import hudson.Extension;
import hudson.model.Computer;
import hudson.node_monitors.AbstractNodeMonitorDescriptor;
import hudson.node_monitors.NodeMonitor;
import hudson.remoting.Callable;
import hudson.slaves.OfflineCause;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.StaplerRequest;

public class KerberosTicketMonitor extends NodeMonitor {

    @Extension
    public static final AbstractNodeMonitorDescriptor<KerberosTicket> DESCRIPTOR = new AbstractNodeMonitorDescriptor<KerberosTicket>() {
    	
    	@Override
    	protected KerberosTicket monitor(Computer c) throws IOException, InterruptedException {
    		
            KerberosTicket ticket = c.getChannel().call(new KerberosMonitorTask());
            
            if (!ticket.isValid() && !c.getName().equals("")) {
            	markOffline(c, new NoKerberosTicket());
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
    
    private static class KerberosMonitorTask implements Callable<KerberosTicket,IOException> {
		private static final long serialVersionUID = 1L;
		
		public KerberosTicket call() throws IOException {
			KerberosTicket ticket = new KerberosTicket();
			
			ProcessBuilder builder = new ProcessBuilder();
			builder.command("ade","oklist");
			Map<String,String> env = builder.environment();
			Process p = builder.start();
			
			try {
				p.waitFor();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String line = null;
			while ((line=reader.readLine())!=null) {
				System.out.println("KerberosTicketMonitorTask:("+java.net.InetAddress.getLocalHost().getHostName()+") -- "+line);
				if (line.contains("krbtgt/DEV.ORACLE.COM@DEV.ORACLE.COM")) {
					System.out.println("found a valid ticket!");
					ticket.setValid();
				}
			}
			
        	return ticket;
        }
    }
    
    static class NoKerberosTicket extends OfflineCause {
		@Override
		public String toString() {
			return "node has no valid kerberos ticket";
		}
    }
 }
