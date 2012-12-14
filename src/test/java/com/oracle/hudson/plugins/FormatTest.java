package com.oracle.hudson.plugins;

import org.junit.Test;

public class FormatTest {
	
	@Test
	public void testFormat() {
		System.out.println(String.format("%04d", 7));
	}
}
