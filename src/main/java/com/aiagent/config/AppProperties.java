package com.aiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant")
public class AppProperties {

    private final App app = new App();
    private final Rag rag = new Rag();
    private final Graph graph = new Graph();
    private final Memory memory = new Memory();

    public App getApp() {
        return app;
    }

    public Rag getRag() {
        return rag;
    }

    public Graph getGraph() {
        return graph;
    }

    public Memory getMemory() {
        return memory;
    }

    public static class App {
        private String name;
        private String defaultLocale;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDefaultLocale() {
            return defaultLocale;
        }

        public void setDefaultLocale(String defaultLocale) {
            this.defaultLocale = defaultLocale;
        }
    }

    public static class Rag {
        private int topK = 5;
        private double similarityThreshold = 0.6;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }
    }

    public static class Graph {
        private int maxIterations = 4;

        public int getMaxIterations() {
            return maxIterations;
        }

        public void setMaxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
        }
    }

    public static class Memory {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
