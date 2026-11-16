package com.smartbanksorter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.game.SpriteOverride;

/**
 * The button in the bank's title bar that opens the category editor: a filing cabinet, on a button drawn like the
 * game's settings and tutorial buttons beside it. The ids are arbitrary, negative so they can't be one of the game's.
 */
@Getter
@RequiredArgsConstructor
enum EditButtonSprite implements SpriteOverride
{
	EDIT(-8363, "edit.png"),
	EDIT_HOVER(-8364, "edit-hover.png");

	private final int spriteId;
	private final String fileName;
}
