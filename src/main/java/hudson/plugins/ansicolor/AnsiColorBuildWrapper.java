/*
 * The MIT License
 *
 * Copyright (c) 2011 Daniel Doubrovkine
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.ansicolor;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.console.LineTransformationOutputStream;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import jenkins.tasks.SimpleBuildWrapper;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Build wrapper that decorates the build's logger to filter output with
 * {@link AnsiHtmlOutputStream}.
 *
 * @author Daniel Doubrovkine
 */
@SuppressWarnings("unused")
public final class AnsiColorBuildWrapper extends SimpleBuildWrapper implements Serializable {

    private final String colorMapName;

    private static final Logger LOG = Logger.getLogger(AnsiColorBuildWrapper.class.getName());

    /**
     * Create a new {@link AnsiColorBuildWrapper}.
     */
    @DataBoundConstructor
    public AnsiColorBuildWrapper(String colorMapName, Integer defaultFg, Integer defaultBg) {
        this.colorMapName = colorMapName;
    }

    public String getColorMapName() {
        return colorMapName == null ? AnsiColorMap.DefaultName : colorMapName;
    }

    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
    }

    /**
     * Registers {@link AnsiColorBuildWrapper} as a {@link BuildWrapper}.
     */
    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        private AnsiColorMap[] colorMaps = new AnsiColorMap[0];

        public DescriptorImpl() {
            super(AnsiColorBuildWrapper.class);
            load();
        }

        private AnsiColorMap[] withDefaults(AnsiColorMap[] colorMaps) {
            Map<String, AnsiColorMap> maps = new LinkedHashMap<String, AnsiColorMap>();
            addAll(AnsiColorMap.defaultColorMaps(), maps);
            addAll(colorMaps, maps);
            return maps.values().toArray(new AnsiColorMap[1]);
        }

        private void addAll(AnsiColorMap[] maps, Map<String, AnsiColorMap> to) {
            for (AnsiColorMap map : maps) {
                to.put(map.getName(), map);
            }
        }

        @Override
        public boolean configure(final StaplerRequest req, final JSONObject formData) throws FormException {
            try {
                setColorMaps(req.bindJSONToList(AnsiColorMap.class,
                        req.getSubmittedForm().get("colorMap")).toArray(new AnsiColorMap[1]));
                return true;
            } catch (ServletException e) {
                throw new FormException(e, "");
            }
        }

        @SuppressWarnings("unused")
        public FormValidation doCheckName(@QueryParameter final String value) {
            return (value.trim().length() == 0) ? FormValidation.error("Name cannot be empty.") : FormValidation.ok();
        }

        public AnsiColorMap[] getColorMaps() {
            return withDefaults(colorMaps);
        }

        public void setColorMaps(AnsiColorMap[] maps) {
            colorMaps = maps;
            save();
        }

        public AnsiColorMap getColorMap(final String name) {
            for (AnsiColorMap colorMap : getColorMaps()) {
                if (colorMap.getName().equals(name)) {
                    return colorMap;
                }
            }
            return AnsiColorMap.Default;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillColorMapNameItems() {
            ListBoxModel m = new ListBoxModel();
            for (AnsiColorMap colorMap : getColorMaps()) {
                String name = colorMap.getName().trim();
                if (name.length() > 0) {
                    m.add(name);
                }
            }
            return m;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillDefaultForegroundItems() {
            ListBoxModel m = new ListBoxModel();

            m.add("Jenkins Default", "");
            for (AnsiColorMap.Color color : AnsiColorMap.Color.values()) {
                m.add(color.toString(), String.valueOf(color.ordinal()));
            }

            return m;
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillDefaultBackgroundItems() {
            return doFillDefaultForegroundItems();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }
    }

    @Override
    public ConsoleLogFilter createLoggerDecorator(Run<?, ?> build) {
        return new ConsoleLogFilterImpl();
    }
    
    protected final class ConsoleLogFilterImpl extends ConsoleLogFilter implements Serializable {

        @Override
        public OutputStream decorateLogger(AbstractBuild build, final OutputStream logger) throws IOException, InterruptedException {
            final AnsiColorMap colorMap = AnsiColorBuildWrapper.this.getDescriptor().getColorMap(colorMapName);

            if (logger == null) {
                return null;
            }

            return new LineTransformationOutputStream() {
                AnsiHtmlOutputStream ansi = new AnsiHtmlOutputStream(logger, colorMap, new AnsiAttributeElement.Emitter() {
                    public void emitHtml(String html) {
                        try {
                            new SimpleHtmlNote(html).encodeTo(logger);
                        } catch (IOException e) {
                            LOG.log(Level.WARNING, "Failed to add HTML markup '" + html + "'", e);
                        }
                    }
                });

                @Override
                protected void eol(byte[] b, int len) throws IOException {
                    ansi.write(b, 0, len);
                    ansi.flush();
                    logger.flush();
                }

                @Override
                public void close() throws IOException {
                    ansi.close();
                    logger.close();
                    super.close();
                }
            };
        }

    }
}
