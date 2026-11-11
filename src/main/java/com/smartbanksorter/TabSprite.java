package com.smartbanksorter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.game.SpriteOverride;

/**
 * The category tabs' backgrounds. These are copies of Bank Tags' tag tab sprites: its own are only registered
 * while it is enabled. The ids are arbitrary, negative so they can't be one of the game's.
 */
@Getter
@RequiredArgsConstructor
enum TabSprite implements SpriteOverride
{
	TAB_BACKGROUND(-8361, "tab.png"),
	TAB_BACKGROUND_ACTIVE(-8362, "tab-active.png");

	private final int spriteId;
	private final String fileName;
}
