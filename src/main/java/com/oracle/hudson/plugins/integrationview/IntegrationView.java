package com.oracle.hudson.plugins.integrationview;

import hudson.Extension;
import hudson.Util;
import hudson.model.TopLevelItem;
import hudson.model.ViewDescriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.Job;
import hudson.model.ListView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class IntegrationView extends ListView {
   /*
    * Use custom CSS style provided by the user
    */

   private boolean useCssStyle = false;
   /*
    * Show standard jobs list at the top of the page
    */
   private boolean includeStdJobList = true;

   @DataBoundConstructor
   public IntegrationView(String name) {
      super(name);
   }

   public boolean isUseCssStyle() {
      return useCssStyle;
   }

   public boolean isIncludeStdJobList() {
      return includeStdJobList;
   }

   /* Use contains */
   //@Deprecated
   public synchronized boolean HasItem(TopLevelItem item) {
      List<TopLevelItem> items = getItems();
      return items.contains(item);
//    return this.contains(item);
   }

   /* Use getItems */
   //@Deprecated
   public synchronized List<Job> getJobs() {
      List<Job> jobs = new ArrayList<Job>();
      
      for (TopLevelItem item : getItems()) {
         if (item instanceof Job) {
            jobs.add((Job) item);
         }
      }

      return jobs;
   }
   
   public synchronized List<Group> getGroups() {
	   List<Group> groups = new ArrayList<Group>();
	   Set<String> names = new HashSet<String>();
	   Pattern pattern = Pattern.compile("(.*)_(.*)");
	   for (Job job: getJobs()) {
		   Matcher m = pattern.matcher(job.getName());
		   if (m.matches()) {
			   if (!names.contains(m.group(1))) {
				   names.add(m.group(1));
				   groups.add(new Group(m.group(1)));
			   }
		   }
	   }
	   
	   return groups;
   }

   @Override
   protected synchronized void submit(StaplerRequest req)
           throws IOException, ServletException, FormException {
      super.submit(req);

      String sIncludeStdJobList = Util.nullify(req.getParameter("includeStdJobList"));
      includeStdJobList = sIncludeStdJobList != null && "on".equals(sIncludeStdJobList);
      includeStdJobList = true;

      String sUseCssStyle = Util.nullify(req.getParameter("useCssStyle"));
      useCssStyle = sUseCssStyle != null && "on".equals(sUseCssStyle);
   }

   @Override
   public void rename(String newName) throws FormException {
      super.rename(newName);
      // Bug 6689 <http://issues.jenkins-ci.org/browse/JENKINS-6689>
      // TODO: if this view is the default view configured in Jenkins, the we must keep it after renaming
   }

   @Extension
   public static final class DescriptorImpl extends ViewDescriptor {

      @Override
      public String getDisplayName() {
         return "ADE integration view";
      }
   }
   
	public final class Group {
		private final String labelName;
		
		public Group(String s) {
			this.labelName = s;
		}
		
		public String getLabelName() {
			return this.labelName;
		}

		public synchronized List<Job> getJobs() {
			List<Job> jobs = new ArrayList<Job>();
			String[] vals = { "prebuild", "build", "postbuild", "postpublish" };
			for (String n : vals) {
				getByName(n, jobs);
			}

			return jobs;
		}

		private void getByName(String n, List<Job> jobs) {
			TopLevelItem item = getJob(labelName + "_" + n);
			if (item instanceof Job) {
				jobs.add((Job) item);
			}
		}
	}
}
