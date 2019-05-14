package it.bologna.ausl.shpeck.service.aspects;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 *
 * @author Salo
 */
//@Aspect
//@Component
//public class ShpeckAspect {
//    
//    @Pointcut("execution(* it.bologna.ausl..*.*(..))")
//    private void anyOperation() {}
//    
//    @Pointcut("execution(* it.bologna.ausl.shpeck.serviceaspects..*.*(..))")
//    private void aspectOperation() {}
//    
//    @Pointcut("anyOperation() && !aspectOperation()")
//    private void operationToLog() {}
//    
//    @Before(value = "operationToLog()")
//    private void logTheEnteringOfMethod(JoinPoint joinPoint){
//        System.out.println("[**] ENTERING: " + joinPoint.getSignature());
//        Object[] argObjects = joinPoint.getArgs();
//        for (Object argObject : argObjects) {
//            System.out.println("\t" + argObject.getClass().getTypeName() + ": " + argObject.toString());
//        }
//        
//    }
//    
//    @AfterReturning(pointcut = "operationToLog()", returning="retVal")
//    private void logTheReturningOfMethod(JoinPoint joinPoint, Object retVal){
//        System.out.println("[<=] RETURNING: " + joinPoint.getSignature() + (retVal != null ? "\t*retVal: " + retVal.toString() : ""));
////        if(retVal != null)
////            System.out.println("\t[<=] *retVal: " + retVal.toString());
//    }
//}
