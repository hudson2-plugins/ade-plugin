package com.oracle.hudson.plugins;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;

import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.HudsonTestCase;

public class AdePluginTest extends HudsonTestCase {

	public void test() throws Exception {
		FreeStyleProject project = createFreeStyleProject();
		project.getBuildWrappersList().add(new AdeViewLauncherDecorator("testview", "SERIES_NAME"));
		project.getBuildersList().add(new UIPBuilder("task1"));
		
		FreeStyleBuild build = project.scheduleBuild2(0).get();
		System.out.println(build.getDisplayName()+" completed");
		
		String s = FileUtils.readFileToString(build.getLogFile());
		System.out.println(s);
	}
}
