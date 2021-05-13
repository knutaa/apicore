package no.paneon.api.logging;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import no.paneon.api.logging.AspectLogger.LogLevel;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogMethod {
	LogLevel level() default LogLevel.DEBUG;
}
