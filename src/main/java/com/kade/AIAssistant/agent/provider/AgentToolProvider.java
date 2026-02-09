package com.kade.AIAssistant.agent.provider;

import com.kade.AIAssistant.agent.tool.RagTools;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentToolProvider {

    private final RagTools ragTools;

    public Object[] getTools() {
        return new Object[]{ragTools};
    }
}
