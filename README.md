# SMS2Telegram

An efficient Android application that forwards SMS messages, emails, and notifications to Telegram with AI-powered verification code detection.

## ✨ Features

### 📱 Message Forwarding
- **SMS Forwarding**: Automatically forward incoming SMS to your Telegram chat
- **Missed Call Alerts**: Get notified about missed or rejected calls
- **Contact Integration**: Display contact names with phone numbers

### 🤖 AI Intelligence
- **Gemini AI Integration**: Automatically detect and extract verification codes from SMS
- **Multiple API Keys**: Support for redundant API keys with automatic rotation
- **Smart Parsing**: Intelligent message parsing and formatting

### 🎨 Material Design Interface
- **Material 3 Design**: Modern, clean Android interface
- **Multiple Themes**: System, Light, and Dark mode support
- **Battery Efficient**: Optimized for minimal battery consumption

### ⚙️ Smart Features
- **Battery Monitoring**: Get alerts when battery is low or fully charged
- **Customizable Thresholds**: Set your own battery alert levels
- **Bot Commands**: Control SMS sending and contacts via Telegram
- **System Logs**: Built-in logging for troubleshooting

### 🔐 Privacy & Security
- **Local Storage**: Messages stored securely on device
- **Secure API**: Direct communication with Telegram API
- **Permission Control**: Granular control over app permissions

## 📦 Installation

1. Download the latest APK from the [Releases](../../releases) page
2. Enable installation from unknown sources in your Android settings
3. Install the APK
4. Grant necessary permissions

## 🚀 Setup

1. **Create a Telegram Bot**:
   - Open Telegram and search for [@BotFather](https://t.me/BotFather)
   - Send `/newbot` and follow the instructions
   - Copy your bot token

2. **Get Your Chat ID**:
   - Start a chat with your bot
   - Send any message
   - Visit `https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getUpdates`
   - Find your `chat.id` in the response

3. **Configure the App**:
   - Open SMS2Telegram
   - Navigate to Settings
   - Enter your Bot Token and Chat ID
   - Click "Save & Verify Connection"

4. **Optional - Add Gemini AI**:
   - Get a free API key from [Google AI Studio](https://makersuite.google.com/app/apikey)
   - Add one or more API keys in the Gemini Configuration section

## 🎮 Usage

### Basic Forwarding
Once configured, the app automatically forwards:
- Incoming SMS messages
- Missed call alerts (if enabled)
- Battery status changes (if enabled)

### Bot Commands
Send these commands to your bot in Telegram:

- `/start` or `help` - Show help menu
- **Send SMS** - Send SMS to any number
- **Browse Contacts** - Send SMS to a contact from your phonebook
- **Search Contact** - Search and send SMS to a specific contact

### Theme Settings
- **System**: Follow system theme
- **Light**: Always use light theme
- **Dark**: Always use dark theme

## 🛠️ Technical Details

### Built With
- **Kotlin**: Modern Android development
- **Jetpack Compose**: Declarative UI framework
- **Material 3**: Latest Material Design components
- **Room Database**: Local data persistence
- **DataStore**: Preferences storage
- **Coroutines & Flow**: Asynchronous programming
- **Retrofit**: Network communication
- **Gemini AI**: Verification code detection

### Architecture
- **MVVM Pattern**: Clean architecture with separation of concerns
- **Repository Pattern**: Data layer abstraction
- **Foreground Service**: Reliable background operation
- **Notification Listener**: Email and app notification monitoring

### Requirements
- Android 8.0 (API 26) or higher
- Internet connection for Telegram and Gemini AI
- SMS permissions for message forwarding
- Notification access for email forwarding

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License

This project is open source and available under the MIT License.

## 🙏 Acknowledgments

- Telegram Bot API
- Google Gemini AI
- Material Design Team
- Android Jetpack Compose Team

## 📧 Support

For issues, questions, or suggestions, please open an issue on GitHub.

---

Made with ❤️ for the Android community
