package com.example.ui.keyboard

/** Small offline catalog. Glyphs come from the Android system font. */
data class EmojiCategory(val name: String, val icon: String, val emojis: List<String>)

object EmojiCatalog {
    val categories = listOf(
        EmojiCategory("Rostos", "😀", "😀 😃 😄 😁 😆 😅 😂 😊 😇 🙂 😉 😍 😘 😗 😙 😚 😋 😛 😜 😝 😎 😏 😒 😞 😔 😟 😕 😣 😖 😫 😩 😢 😭 😤 😠 😡 😳 😱 😨 😰 😥 😓 😴 😪 😷 😶 😐 😑 🙄 🤔 🤗 🤓".split(" ")),
        EmojiCategory("Gestos", "👍", "👍 👎 👊 ✊ ✌️ 👌 👏 🙌 👐 🙏 💪 👋 ☝️ 👆 👇 👈 👉 ✋ 🖐️ 🤘 ✍️ 🖖 🤞 🤙".split(" ")),
        EmojiCategory("Corações", "❤️", "❤️ 💛 💚 💙 💜 🖤 💔 💕 💞 💓 💗 💖 💘 💝 💟 ❣️ 💌 💋 💐 🌹 🌸 🌷 🌻 🌺".split(" ")),
        EmojiCategory("Animais", "🐶", "🐶 🐱 🐭 🐹 🐰 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦄 🐝 🐢 🐍 🐬 🐟".split(" ")),
        EmojiCategory("Comida", "🍕", "🍎 🍏 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍍 🍅 🥕 🌽 🍞 🧀 🍔 🍟 🍕 🌭 🍿 🍰 🎂 🍫 🍬 🍭 🍪 ☕ 🍵 🍺 🍷".split(" ")),
        EmojiCategory("Objetos", "⚽", "⚽ 🏀 🏈 ⚾ 🎾 🏐 🎱 🏆 🏅 🎉 🎊 🎁 🎈 🎵 🎶 🎧 🎤 🎮 📱 💻 📷 📚 ✏️ 💡 🔑 🔒 ⏰ 📅 ✅ ❌ ⭐ 🔥 💯".split(" ")),
        EmojiCategory("Bandeiras", "🇧🇷", "🇧🇷 🇵🇹 🇺🇸 🇬🇧 🇪🇸 🇫🇷 🇩🇪 🇮🇹 🇯🇵 🇰🇷 🇨🇳 🇨🇦 🇲🇽 🇦🇷 🇨🇱 🇦🇺".split(" "))
    )
}
