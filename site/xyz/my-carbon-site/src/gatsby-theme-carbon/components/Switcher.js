import React from 'react';
import Switcher from 'gatsby-theme-carbon/src/components/Switcher';
import SwitcherLink from 'gatsby-theme-carbon/src/components/Switcher';
import SwitcherDivider from 'gatsby-theme-carbon/src/components/Switcher';

// Reference https://github.com/carbon-design-system/gatsby-theme-carbon/blob/master/packages/gatsby-theme-carbon/src/components/Switcher/Switcher.js 
const CustomChildren = () => (
  <>
    <SwitcherDivider>Key Links</SwitcherDivider>
    <SwitcherLink href="https://github.com/IBM/FHIR">GitHub</SwitcherLink>
    <SwitcherLink href="https://chat.fhir.org/#narrow/stream/212434-ibm">Zulip: #ibm</SwitcherLink>
    <SwitcherDivider>JavaDoc</SwitcherDivider> 
    <SwitcherLink href="https://ibm.github.io/FHIR/javadoc/4.0.0/">4.0.0</SwitcherLink>
    <SwitcherLink href="https://ibm.github.io/FHIR/javadoc/4.0.0/">last</SwitcherLink>
  </>
);

const CustomSwitcher = ({ children }) => {
  return (
    <Switcher>
        {children}
    </Switcher>
  );
};

CustomSwitcher.defaultProps = {
  children: <CustomChildren />,
};

export default CustomSwitcher;