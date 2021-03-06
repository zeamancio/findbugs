package jsr305;


import javax.annotation.meta.When;

import edu.umd.cs.findbugs.annotations.NoWarning;

public class TestInheritedAnnotations implements I1, I2 {
	
	@Foo(when=When.ALWAYS) Object always;
	@Foo(when=When.NEVER) Object never;

	@NoWarning("TQ")
	public Object alwaysReturnFoo1() {
		return always;
	}

	@NoWarning("TQ")
	public Object neverReturnFoo1() {
		return never;
	}

	@NoWarning("TQ")
	public Object alwaysReturnFooParams1(Object alwaysParam, Object neverParam) {
		return alwaysParam;
	}
}
