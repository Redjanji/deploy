package com.xss.gatewayservice.config;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class SentinelConfig {

    public static String getBlockType(BlockException e) {
        if (e instanceof FlowException) {
            return "FLOW_CONTROL";
        } else if (e instanceof DegradeException) {
            return "DEGRADE";
        } else if (e instanceof ParamFlowException) {
            return "PARAM_FLOW_CONTROL";
        } else if (e instanceof AuthorityException) {
            return "AUTHORITY";
        } else if (e instanceof SystemBlockException) {
            return "SYSTEM_BLOCK";
        }
        return "UNKNOWN";
    }

    public static String getBlockRule(BlockException e) {
        if (e instanceof FlowException) {
            FlowException fe = (FlowException) e;
            return String.format("resource=%s, grade=%s, count=%s", 
                    fe.getRule().getResource(),
                    fe.getRule().getGrade() == 0 ? "QPS" : "THREAD",
                    fe.getRule().getCount());
        } else if (e instanceof DegradeException) {
            DegradeException de = (DegradeException) e;
            return String.format("resource=%s, count=%s, timeWindow=%ds",
                    de.getRule().getResource(),
                    de.getRule().getCount(),
                    de.getRule().getTimeWindow());
        } else if (e instanceof ParamFlowException) {
            ParamFlowException pe = (ParamFlowException) e;
            return String.format("resource=%s, paramIdx=%d, count=%s",
                    pe.getRule().getResource(),
                    pe.getRule().getParamIdx(),
                    pe.getRule().getCount());
        } else if (e instanceof AuthorityException) {
            AuthorityException ae = (AuthorityException) e;
            return String.format("resource=%s, strategy=%s",
                    ae.getRule().getResource(),
                    ae.getRule().getStrategy() == 0 ? "WHITE_LIST" : "BLACK_LIST");
        } else if (e instanceof SystemBlockException) {
            SystemBlockException se = (SystemBlockException) e;
            return String.format("resource=%s", se.getRule().getResource());
        }
        return e.getMessage();
    }
}