// zen_hook_installer.h
#pragma once

/**
 * ZenHookInstaller
 *
 * Installs all runtime hooks into the Bedrock address space.
 * Called from ANativeActivity_onCreate BEFORE handing off to Bedrock's
 * own onCreate, so every hook is in place when Bedrock starts initializing.
 */
namespace ZenHookInstaller {
    /**
     * installAll()
     * Entry point called from zen_native_activity.cpp.
     * Internally calls individual install functions per module.
     */
    void installAll();
}
