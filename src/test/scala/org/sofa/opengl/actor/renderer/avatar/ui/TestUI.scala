package org.sofa.opengl.actor.renderer.avatar.ui

import org.scalatest.FlatSpec

import akka.actor.ActorSystem

import org.sofa.opengl.{Shader}
import org.sofa.opengl.actor.renderer.{AvatarName}
import org.sofa.opengl.actor.renderer.backend.RendererNewt
import org.sofa.opengl.text.GLFont


class TestUI extends FlatSpec {

	// The renderer to test.
	val renderer = new RendererNewt(new UIAvatarFactory())//actorSystem.deadLetters)

	"A UI" should "allow adding a root" in {
		renderer.start("title", initialWidth=320, initialHeight=240, fps=24, decorated=false, fullscreen=false, overSample=4)

		Shader.path += "/Users/antoine/Documents/Programs/SOFA/src/main/scala/org/sofa/opengl/shaders/es2"
		Shader.path += "shaders"
		GLFont.path += "/Users/antoine/Library/Fonts"
		GLFont.path += "Fonts"

		var counter = 0

		while(! renderer.isInitialized) {
			Thread.sleep(1)
			counter += 1
			assertResult(true) {counter < 480}	// should suffice ?
		}

		renderer.addScreen("default-screen")
		assertResult(1, "only one screen") { renderer.screenCount }
		
		renderer.switchToScreen("default-screen")
		assertResult(true, "renderer has a current screen") {renderer.hasCurrentScreen}
		
		renderer.currentScreen.addAvatar(AvatarName("root"), "ui.root")
		assertResult(1, "only one avatar") { renderer.currentScreen.subCount }

		renderer.currentScreen.addAvatar(AvatarName("root.list"), "ui.list")

		renderer.currentScreen.addAvatar(AvatarName("root.list.item1"), "ui.list-item")
		renderer.currentScreen.addAvatar(AvatarName("root.list.item2"), "ui.list-item")
		renderer.currentScreen.addAvatar(AvatarName("root.list.item3"), "ui.list-item")
		renderer.currentScreen.addAvatar(AvatarName("root.list.item4"), "ui.list-item")
		renderer.currentScreen.addAvatar(AvatarName("root.list.item5"), "ui.list-item")
		renderer.currentScreen.addAvatar(AvatarName("root.list.item6"), "ui.list-item")
	}

	it should "exit gracefully" in {
		Thread.sleep(60000)
		renderer.destroy
	}
}