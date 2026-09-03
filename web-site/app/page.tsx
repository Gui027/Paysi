import { CtaBanner } from "@/components/CtaBanner";
import { Faq } from "@/components/Faq";
import { FeatureGrid } from "@/components/FeatureGrid";
import { Footer } from "@/components/Footer";
import { Header } from "@/components/Header";
import { Hero } from "@/components/Hero";
import { LogosRow } from "@/components/LogosRow";
import { MagicSteps } from "@/components/MagicSteps";
import { StatsRow } from "@/components/StatsRow";
import { Timeline } from "@/components/Timeline";
import { ConversionTabs } from "@/components/ConversionTabs";

export default function Home() {
  return (
    <>
      <Header />
      <main id="recursos">
        <Hero />
        <StatsRow />
        <LogosRow />
        <ConversionTabs />
        <Timeline />
        <MagicSteps />
        <FeatureGrid />
        <CtaBanner />
        <Faq />
      </main>
      <Footer />
    </>
  );
}
