package com.oracle.hudson.plugins.integrationview;

import hudson.Extension;
import hudson.Util;
import hudson.model.BallColor;
import hudson.model.TopLevelItem;
import hudson.model.ViewDescriptor;
import hudson.model.AbstractProject;
import hudson.model.Descriptor.FormException;
import hudson.model.Job;
import hudson.model.ListView;
import hudson.model.Run;

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

	private static BallColor[] colors = {BallColor.GREY,BallColor.BLUE,BallColor.YELLOW,BallColor.RED,BallColor.DISABLED,BallColor.ABORTED};
	
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
   
	private static boolean lessThan(BallColor current, BallColor newColor) {
		// TODO memoize since this could get called a lot
		return (getOrdinal(current)<getOrdinal(newColor));
	}
	
	private static int getOrdinal(BallColor color) {
		for (int i= 0; i<colors.length; i++) {
			if (color.equals(colors[i])) {
				return i;
			}
		}
		return 0;
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

		public BallColor getIconColor() {
			BallColor color = BallColor.GREY;
			try {
				for (String n: JobSuffixes.vals) {
					Job job = this.getJob(n);
					if (isDisabled(job)) {
						continue;
					}
					Run lastBuild = this.lastFinishedBuild(job);
					
					if (lastBuild!=null && lastBuild.getIconColor().equals(BallColor.RED)) {
						color = ("build".equals(n))?BallColor.RED:BallColor.RED;
					} else if (lastBuild!=null) {
						// if new color is less than in the sense GREY < BLUE < YELLOW < RED < DISABLED < ABORTED
						// then update it since the top-level report is a "worse-case" scenario
						if (lessThan(color,lastBuild.getIconColor())) {
							color = lastBuild.getIconColor();
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return color;
		}
		
		private Boolean isDisabled(Job job) {
			try {
				return ((AbstractProject)job).isDisabled();
			} catch (ClassCastException e) {
				return false;
			}
		}
		
		public synchronized List<Job> getJobs() {
			List<Job> jobs = new ArrayList<Job>();
			for (String n : JobSuffixes.vals) {
				getByName(n, jobs);
			}

			return jobs;
		}
		
		/*
		 * search for Runs that are finished - don't return a Run that hasn't actually started yet
		 */
		private Run lastFinishedBuild(Job j) {
			Run lastBuild = j.getLastBuild();
			while(lastBuild!=null&&lastBuild.hasntStartedYet()) {
				lastBuild = lastBuild.getPreviousBuild();
			}
			return lastBuild;
		}
		
		private Job getJob(String n) {
			TopLevelItem item = IntegrationView.this.getJob(labelName+"_"+n);
			if (item instanceof Job) {
				return (Job)item;
			}
			return null;
		}
		
		private void getByName(String n, List<Job> jobs) {
			Job job = getJob(n);
			if (job!=null) {
				jobs.add(job);
			}
		}
	}
}
