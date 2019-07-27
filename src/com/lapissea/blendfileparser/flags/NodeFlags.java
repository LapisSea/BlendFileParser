package com.lapissea.blendfileparser.flags;

import com.lapissea.util.UtilL;

public enum NodeFlags implements FlagEnum{
	
	SELECT(1),
//	OPTIONS(2),
	PREVIEW(4),
	HIDDEN(8),
	ACTIVE(16),
	ACTIVE_ID(32),
	DO_OUTPUT(64),
	//	__GROUP_EDIT (128), /* DEPRECATED */
//	/* free test flag, undefined */
//	TEST(256),
	/* node is disabled */
	MUTED(512),
	
	// CUSTOM_NAME (1024),    /* deprecated! */
	/* group node types: use const outputs by default */
	CONST_OUTPUT(1<<11),
	
	/* node is always behind others */
	BACKGROUND(1<<12),
	
	/* automatic flag for nodes included in transforms */
	TRANSFORM(1<<13),
	/* node is active texture */
	
	ACTIVE_TEXTURE(1<<14),
	
	/* use a custom color for the node */
	CUSTOM_COLOR(1<<15),
	
//	/* Node has been initialized
//	 * This flag indicates the node->typeinfo->init function has been called.
//	 * In case of undefined type at creation time this can be delayed until
//	 * until the node type is registered.
//	 */
//	INIT(1<<16),
//
//	/* do recalc of output, used to skip recalculation of unwanted
//	 * composite out nodes when editing tree
//	 */
//	DO_OUTPUT_RECALC(1<<17),
	;
	
	public final int handle;
	
	NodeFlags(int handle){
		this.handle=handle;
	}
	
	@Override
	public boolean matchesFlag(int flags){
		return UtilL.checkFlag(flags, handle);
	}
}
