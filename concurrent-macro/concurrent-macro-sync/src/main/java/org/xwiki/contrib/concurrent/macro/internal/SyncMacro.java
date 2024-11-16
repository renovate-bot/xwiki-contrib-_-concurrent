/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.concurrent.macro.internal;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.collections4.map.ReferenceMap;
import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.concurrent.macro.SyncMacroParameters;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.Block.Axes;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.MetaDataBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.MetadataBlockMatcher;
import org.xwiki.rendering.listener.MetaData;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.macro.MacroContentParser;
import org.xwiki.rendering.macro.MacroExecutionException;
import org.xwiki.rendering.macro.descriptor.DefaultContentDescriptor;
import org.xwiki.rendering.transformation.MacroTransformationContext;

/**
 * Make sure the content of the macro is executed only once per thread.
 * 
 * @version $Id$
 */
@Component
@Named("sync")
@Singleton
public class SyncMacro extends AbstractMacro<SyncMacroParameters>
{
    /**
     * The description of the macro.
     */
    private static final String DESCRIPTION =
        "Help making sure the content of the macro is executed by only one thread at a time.";

    /**
     * The description of the macro content.
     */
    private static final String CONTENT_DESCRIPTION = "The content to execute";

    @Inject
    private MacroContentParser parser;

    private Map<String, Lock> locks = Collections.synchronizedMap(new ReferenceMap<>());

    /**
     * Create and initialize the descriptor of the macro.
     */
    public SyncMacro()
    {
        super("Sync", DESCRIPTION, new DefaultContentDescriptor(CONTENT_DESCRIPTION, false, Block.LIST_BLOCK_TYPE),
            SyncMacroParameters.class);

        setDefaultCategory(DEFAULT_CATEGORY_DEVELOPMENT);
    }

    @Override
    public boolean supportsInlineMode()
    {
        return true;
    }

    @Override
    public List<Block> execute(SyncMacroParameters parameters, String content, MacroTransformationContext context)
        throws MacroExecutionException
    {
        // Identify the macro
        String id = getId(parameters, context);

        // Get the corresponding lock
        Lock lock = this.locks.computeIfAbsent(id, k -> new ReentrantLock(true));

        // Execute the macro in a lock
        lock.lock();
        try {
            XDOM xdom = this.parser.parse(content, context, true, context.isInline());

            return Collections.singletonList(new MetaDataBlock(xdom.getChildren(), getNonGeneratedContentMetaData()));
        } finally {
            lock.unlock();
        }
    }

    private String getId(SyncMacroParameters parameters, MacroTransformationContext context)
    {
        String id = parameters.getId();

        // If not explicit id is provided, deduce it from the macro location
        if (StringUtils.isEmpty(id)) {
            // Rely on the source reference and the macro index in the source
            id = getCurrentSource(context) + '-' + getMacroIndex(context);
        }

        return id;
    }

    protected String getCurrentSource(MacroTransformationContext context)
    {
        String currentSource = null;

        if (context != null) {
            currentSource =
                context.getTransformationContext() != null ? context.getTransformationContext().getId() : null;

            MacroBlock currentMacroBlock = context.getCurrentMacroBlock();

            if (currentMacroBlock != null) {
                MetaDataBlock metaDataBlock =
                    currentMacroBlock.getFirstBlock(new MetadataBlockMatcher(MetaData.SOURCE), Axes.ANCESTOR_OR_SELF);

                if (metaDataBlock != null) {
                    currentSource = (String) metaDataBlock.getMetaData().getMetaData(MetaData.SOURCE);
                }
            }
        }

        return currentSource;
    }

    protected long getMacroIndex(MacroTransformationContext context)
    {
        return context.getXDOM().indexOf(context.getCurrentMacroBlock());
    }
}
