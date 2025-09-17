# 'dockerOrch' DSL Usage Guide

This document provides simple, informal examples of how to use the 'dockerOrch' (e.g., 'docker compose') DSL for the 
'gradle-docker' plugin.

## Prerequisites

First, add the plugin to your `build.gradle`:

```gradle
plugins {
    id 'com.kineticfire.gradle.gradle-docker' version '1.0.0'
}
```