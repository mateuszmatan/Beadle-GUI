package com.bbh.itss.beadle.support;

import com.bbh.itss.beadle.adapter.out.cybertrack.CyberTrackProperties;
import com.bbh.itss.beadle.adapter.out.cybertrack.DemoCyberTrackAdapter;
import com.bbh.itss.beadle.adapter.out.cybertrack.JiraCyberTrackAdapter;
import com.bbh.itss.beadle.adapter.out.jira.DemoJiraAdapter;
import com.bbh.itss.beadle.adapter.out.protech.DemoProTechAdapter;
import com.bbh.itss.beadle.adapter.out.protech.DemoProTechChanges;
import com.bbh.itss.beadle.adapter.out.protech.DemoProTechLookups;
import com.bbh.itss.beadle.adapter.out.protech.DemoProTechProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Beadle-Internal and Beadle-ProTech in process, for the test suites: profile {@code stub-services} puts the demo
 * Jira, CyberTrack and ProTech Beadle-GUI carried before the two services owned them in front of the REST adapters,
 * and stores the demo changes when {@code beadle.demo-data} is on. The unit and regression suites and the smoke suite
 * without {@code -Dsmoke.baseUrl} run with it.
 */
@Configuration(proxyBeanMethods = false)
@Profile(StubServices.PROFILE)
@EnableConfigurationProperties({CyberTrackProperties.class, DemoProTechProperties.class})
@Import({DemoJiraAdapter.class, DemoCyberTrackAdapter.class, JiraCyberTrackAdapter.class, DemoProTechAdapter.class,
        DemoProTechLookups.class, DemoProTechChanges.class})
public class StubServices {

    public static final String PROFILE = "stub-services";
}
