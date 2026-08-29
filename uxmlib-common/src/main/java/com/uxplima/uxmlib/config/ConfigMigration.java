package com.uxplima.uxmlib.config;

import java.util.Objects;

import org.spongepowered.configurate.NodePath;
import org.spongepowered.configurate.transformation.ConfigurationTransformation;
import org.spongepowered.configurate.transformation.TransformAction;

/**
 * Describes how to upgrade a config across schema versions, so an old file is brought current without the
 * user re-creating it. A {@code config-version} key records which version the file is at; each numbered
 * step renames, moves, or removes keys, and on load only the steps newer than the stored version replay,
 * after which the version key is rewritten. Built fluently with {@link #builder()} over friendly dotted
 * paths: Configurate's versioned-transformation machinery is wrapped, not exposed.
 */
public final class ConfigMigration {

    private final ConfigurationTransformation.Versioned transformation;

    private ConfigMigration(ConfigurationTransformation.Versioned transformation) {
        this.transformation = transformation;
    }

    /** Start building a migration; the version is recorded under the {@code config-version} key. */
    public static Builder builder() {
        return new Builder();
    }

    /** The highest version this migration upgrades to. */
    public int latestVersion() {
        return transformation.latestVersion();
    }

    /** The version of {@code node}, or {@link ConfigurationTransformation.Versioned#VERSION_UNKNOWN}. */
    int versionOf(org.spongepowered.configurate.ConfigurationNode node) {
        return transformation.version(node);
    }

    /** Apply the upgrade steps to {@code node} in place, returning the version it ended at. */
    int apply(org.spongepowered.configurate.ConfigurationNode node) {
        try {
            transformation.apply(node);
        } catch (org.spongepowered.configurate.ConfigurateException failure) {
            throw new ConfigException("failed to migrate config", failure);
        }
        return transformation.version(node);
    }

    /** Fluent builder collecting numbered upgrade steps. */
    public static final class Builder {
        private final ConfigurationTransformation.VersionedBuilder versioned =
                ConfigurationTransformation.versionedBuilder().versionKey("config-version");

        private Builder() {}

        /** Add the upgrade {@code step} that brings the config to version {@code version}. */
        public Builder version(int version, java.util.function.Consumer<Step> step) {
            Objects.requireNonNull(step, "step");
            versioned.makeVersion(version, builder -> step.accept(new Step(builder)));
            return this;
        }

        /** Build the migration. */
        public ConfigMigration build() {
            return new ConfigMigration(versioned.build());
        }
    }

    /** The actions available within one upgrade step, over friendly dotted paths. */
    public static final class Step {
        private final ConfigurationTransformation.Builder builder;

        private Step(ConfigurationTransformation.Builder builder) {
            this.builder = builder;
        }

        /** Rename the key at {@code path} to {@code newName} (keeping its value). */
        public Step rename(String path, String newName) {
            builder.addAction(path(path), TransformAction.rename(newName));
            return this;
        }

        /** Remove the key at {@code path}. */
        public Step remove(String path) {
            builder.addAction(path(path), TransformAction.remove());
            return this;
        }

        private static NodePath path(String dotted) {
            return NodePath.of((Object[]) dotted.split("\\."));
        }
    }
}
