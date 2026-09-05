# AudioConverter টেস্ট অ্যাপ

আপনার তৈরি করা কাস্টম `AudioConverter` (FFmpeg-চালিত) AAR লাইব্রেরিটি রিয়েল ডিভাইসে টেস্ট করার জন্য এটি একটি অত্যন্ত সাদামাটা, শুধুমাত্র **Java + XML** দিয়ে বানানো Android প্রজেক্ট।

## অ্যাপ কী করে

1. **অডিও ফাইল সিলেক্ট করুন** — সিস্টেম ফাইল পিকার থেকে যেকোনো অডিও ফাইল (mp3/wav/m4a/flac ইত্যাদি) বেছে নিন।
2. **কনভার্ট করুন** — সিলেক্ট করা ফাইলটি `AudioConverter` লাইব্রেরি দিয়ে MP3 (192kbps, 44.1kHz, Stereo) এ কনভার্ট হবে।
3. **প্লে করুন** — কনভার্ট হওয়া আউটপুট ফাইলটি সাথে সাথে অ্যাপের ভেতরেই প্লে করে শোনা যাবে।

সম্পূর্ণ কনভার্সন `app/cacheDir`-এর ভেতরে হয় বলে কোনো স্টোরেজ পারমিশন ছাড়াই কাজ করবে।

## প্রজেক্ট স্ট্রাকচার

```
AudioConverterTest/
├── app/
│   ├── build.gradle                # fileTree(dir: 'libs') দিয়ে AAR অটো-যুক্ত হয়
│   ├── libs/                       # ← এখানে আপনার audioconverter-release.aar রাখুন
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/.../MainActivity.java
│       └── res/
│           ├── layout/activity_main.xml
│           └── values/ (strings.xml, styles.xml)
├── .github/workflows/build-apk.yml # GitHub Actions দিয়ে অটোমেটিক APK বিল্ড
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## ব্যবহারের ধাপ

### ১. লাইব্রেরি ফাইল যুক্ত করুন
`audioconverter-release.aar` ফাইলটি `app/libs/` ফোল্ডারে আপলোড করুন (GitHub ওয়েব ইন্টারফেস থেকেও করা যাবে — শুধু ফোল্ডারে গিয়ে "Add file" করুন)।

> নাম যাই হোক, `.aar` এক্সটেনশন থাকলেই `app/build.gradle`-এর `fileTree(dir: 'libs', include: ['*.aar','*.jar'])` অংশটি সেটিকে অটোমেটিক ডিপেন্ডেন্সি হিসেবে যুক্ত করে নেবে।

### ২. GitHub-এ পুশ করুন
পুরো এই ফোল্ডারটি একটি নতুন GitHub রিপোজিটরিতে পুশ করুন (মূল ব্রাঞ্চের নাম `main` রাখাই ভালো, কারণ ওয়ার্কফ্লো `main`-এ পুশ হলে অটো ট্রিগার হয়)।

### ৩. ওয়ার্কফ্লো রান করুন
- `main` ব্রাঞ্চে পুশ করলেই `.github/workflows/build-apk.yml` অটোমেটিক রান হবে, অথবা
- GitHub রিপোর **Actions** ট্যাব থেকে "Build Debug APK" ওয়ার্কফ্লো সিলেক্ট করে **Run workflow** বাটনে ক্লিক করুন (`workflow_dispatch` যুক্ত করা আছে)।

### ৪. APK ডাউনলোড করুন
ওয়ার্কফ্লো সফলভাবে শেষ হলে, সেই রান-এর পেজে নিচে **Artifacts** সেকশনে `app-debug-apk` নামে একটি জিপ ফাইল পাবেন — এর ভেতরেই আপনার তৈরি `.apk` ফাইলটি থাকবে।

## গুরুত্বপূর্ণ নোট

- লাইব্রেরির ডকুমেন্টেশন অনুযায়ী এটি শুধু `arm64-v8a` আর্কিটেকচার সাপোর্ট করে, তাই `app/build.gradle`-এ `ndk { abiFilters 'arm64-v8a' }` আগে থেকেই সেট করা আছে।
- এই প্রজেক্টে Gradle Wrapper ফাইল (`gradlew`, `gradle-wrapper.jar`) রাখা হয়নি — GitHub Actions ওয়ার্কফ্লো নিজে থেকেই CI-তে `gradle wrapper --gradle-version 8.7` কমান্ড দিয়ে সেগুলো তৈরি করে নেয়, তাই রিপোজিটরি হালকা থাকে।
- Android Studio-তে লোকালি ওপেন করতে চাইলে, স্টুডিও নিজে থেকেই Gradle Wrapper জেনারেট করে নেবে (Sync করার সময়)।
- টেস্ট করার সময় Logcat-এ `AudioConverterTest` ট্যাগ দিয়ে ফিল্টার করলে কনভার্সন সংক্রান্ত এরর/লগ দেখতে পাবেন।
