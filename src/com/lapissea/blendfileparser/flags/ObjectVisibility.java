package com.lapissea.blendfileparser.flags;

import com.lapissea.blendfileparser.Struct;
import com.lapissea.util.UtilL;

@SuppressWarnings("PointlessBitwiseExpression")
public enum ObjectVisibility implements FlagEnum{
	
	/* User controlled flags. */
	BASE_SELECTED(1<<0), /* Object is selected. */
	BASE_HIDDEN(1<<8),   /* Object is hidden for editing. */
	
	/* Runtime evaluated flags. */
	BASE_VISIBLE(1<<1),    /* Object is enabled and visible. */
	BASE_SELECTABLE(1<<2), /* Object can be selected. */
	BASE_FROM_DUPLI(1<<3), /* Object comes from duplicator. */
	/* BASE_DEPRECATED    (1 << 4), */
	BASE_FROM_SET(1<<5),         /* Object comes from set. */
	BASE_ENABLED_VIEWPORT(1<<6), /* Object is enabled in viewport. */
	BASE_ENABLED_RENDER(1<<7),   /* Object is enabled in final render */
	/* BASE_DEPRECATED          (1 << 9), */
	BASE_HOLDOUT(1<<10),       /* Object masked out from render */
	BASE_INDIRECT_ONLY(1<<11); /* Object only contributes indirectly to render */
	
	public final int handle;
	
	ObjectVisibility(int handle){
		this.handle=handle;
	}
	
	public boolean isEnabled(Struct.Instance object){
		return isEnabled(object.getShort("base_flag"));
	}
	
	public boolean isEnabled(int flags){
		return UtilL.checkFlag(flags, handle);
	}
	
	@Override
	public boolean matchesFlag(int flags){
		return UtilL.checkFlag(flags, handle);
	}
}
