package com.xss.gatewayservice.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SentinelFlowRuleConfig {

    @PostConstruct
    public void initRules() {
        initFlowRules();
        initDegradeRules();
    }

    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule dictRule = new FlowRule();
        dictRule.setResource("GET:/api/dict/items");
        dictRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        dictRule.setCount(50);
        dictRule.setLimitApp("default");
        dictRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rules.add(dictRule);

        FlowRule propertyListRule = new FlowRule();
        propertyListRule.setResource("GET:/api/properties");
        propertyListRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        propertyListRule.setCount(30);
        propertyListRule.setLimitApp("default");
        propertyListRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rules.add(propertyListRule);

        FlowRule tokenRule = new FlowRule();
        tokenRule.setResource("POST:/token");
        tokenRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        tokenRule.setCount(20);
        tokenRule.setLimitApp("default");
        tokenRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        rules.add(tokenRule);

        FlowRuleManager.loadRules(rules);
        log.info("[Sentinel] 加载限流规则: {} 条", rules.size());
        for (FlowRule rule : rules) {
            log.info("[Sentinel] 限流规则: resource={}, grade=QPS, count={}",
                    rule.getResource(), rule.getCount());
        }
    }

    private void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        String[] resources = {"GET:/api/dict/items", "GET:/api/properties", "POST:/token"};

        for (String resource : resources) {
            DegradeRule rule = new DegradeRule();
            rule.setResource(resource);
            rule.setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
            rule.setCount(0.5);
            rule.setTimeWindow(10);
            rule.setMinRequestAmount(5);
            rule.setStatIntervalMs(1000);
            rules.add(rule);
        }

        DegradeRuleManager.loadRules(rules);
        log.info("[Sentinel] 加载熔断规则: {} 条", rules.size());
        for (DegradeRule rule : rules) {
            log.info("[Sentinel] 熔断规则: resource={}, grade=EXCEPTION_RATIO, threshold={}, timeWindow={}s, minRequest={}",
                    rule.getResource(), rule.getCount(), rule.getTimeWindow(), rule.getMinRequestAmount());
        }
    }
}
