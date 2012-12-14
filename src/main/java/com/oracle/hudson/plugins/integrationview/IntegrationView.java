package com.oracle.hudson.plugins.integrationview;

import hudson.DescriptorExtensionList;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.model.ViewDescriptor;
import hudson.model.Descriptor.FormException;
import hudson.model.ListView;
import java.io.IOException;

import java.io.UnsupportedEncodingException;
import java.util.*;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

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

   @Override
   protected synchronized void submit(StaplerRequest req)
           throws IOException, ServletException, FormException {
      super.submit(req);

      String sIncludeStdJobList = Util.nullify(req.getParameter("includeStdJobList"));
      includeStdJobList = sIncludeStdJobList != null && "on".equals(sIncludeStdJobList);

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
}
