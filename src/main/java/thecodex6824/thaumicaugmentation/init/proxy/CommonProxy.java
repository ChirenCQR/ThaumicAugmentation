/**
 *	Thaumic Augmentation
 *	Copyright (c) 2019 TheCodex6824.
 *
 *  This file is part of Thaumic Augmentation.
 *
 *  Thaumic Augmentation is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Thaumic Augmentation is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Thaumic Augmentation.  If not, see <https://www.gnu.org/licenses/>.
 */

package thecodex6824.thaumicaugmentation.init.proxy;

import com.google.common.collect.ImmutableMap;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.animation.ITimeValue;
import net.minecraftforge.common.model.animation.IAnimationStateMachine;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import thecodex6824.thaumicaugmentation.ThaumicAugmentation;
import thecodex6824.thaumicaugmentation.client.gui.GUIHandler;
import thecodex6824.thaumicaugmentation.init.MiscHandler;
import thecodex6824.thaumicaugmentation.init.RecipeHandler;
import thecodex6824.thaumicaugmentation.init.ResearchHandler;

public class CommonProxy implements ISidedProxy {

	public IAnimationStateMachine loadASM(ResourceLocation loc, ImmutableMap<String, ITimeValue> params) {
		return null;
	}
	
	@Override
	public void preInit() {
		NetworkRegistry.INSTANCE.registerGuiHandler(ThaumicAugmentation.instance, new GUIHandler());
	}
	
	@Override
	public void init() {
		RecipeHandler.init();
		ResearchHandler.init();
		MiscHandler.init();
	}
	
	@Override
	public void postInit() {}
	
}