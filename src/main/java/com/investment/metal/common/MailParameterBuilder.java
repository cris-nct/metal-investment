package com.investment.metal.common;

import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MailParameterBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailParameterBuilder.class);

    private static final Map<MailTemplates, String> mailTemplates = new HashMap<>();

    static {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        for (MailTemplates templateType : MailTemplates.values()) {
            final String template;
            try {
                template = IOUtils.toString(
                        Objects.requireNonNull(contextClassLoader.getResourceAsStream("mail-templates/" + templateType.getFilename())),
                        Charsets.UTF_8);
                MailParameterBuilder.mailTemplates.put(templateType, template);
            } catch (IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    private final String text;

    private final Map<String, Object> parameters = new HashMap<>();

    private MailParameterBuilder(String text) {
        this.text = text;
    }

    public static MailParameterBuilder newInstance(MailTemplates template) {
        return new MailParameterBuilder(MailParameterBuilder.mailTemplates.get(template));
    }

    public MailParameterBuilder replace(String name, Object value) {
        this.parameters.put(name, value);
        return this;
    }

    public MailParameterBuilder replaceDouble(String argumentName, Double value, int decimals) {
        String paramValue = String.format("%." + decimals + "f", value);
        return this.replace(argumentName, paramValue);
    }

    public String build() {
        String result = this.text;
        for (Map.Entry<String, Object> entry : this.parameters.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue().toString());
        }
        return result;
    }

}
