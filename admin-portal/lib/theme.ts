import type { ThemeConfig } from 'antd';

// Your custom color palette
export const colors = {
  rich_black: {
    DEFAULT: '#01161e',
    100: '#000406',
    200: '#00090c',
    300: '#010d12',
    400: '#011118',
    500: '#01161e',
    600: '#04597b',
    700: '#079cd8',
    800: '#45c6f9',
    900: '#a2e3fc'
  },
  midnight_green: {
    DEFAULT: '#124559',
    100: '#040e12',
    200: '#071c24',
    300: '#0b2935',
    400: '#0f3747',
    500: '#124559',
    600: '#20799c',
    700: '#36a9d6',
    800: '#79c5e4',
    900: '#bce2f1'
  },
  air_force_blue: {
    DEFAULT: '#598392',
    100: '#121a1d',
    200: '#24343a',
    300: '#354e57',
    400: '#476874',
    500: '#598392',
    600: '#769dab',
    700: '#99b6c0',
    800: '#bbced5',
    900: '#dde7ea'
  },
  ash_gray: {
    DEFAULT: '#aec3b0',
    100: '#1f2a20',
    200: '#3e5441',
    300: '#5e7f61',
    400: '#83a386',
    500: '#aec3b0',
    600: '#bdcebf',
    700: '#cedbcf',
    800: '#dee7df',
    900: '#eff3ef'
  },
  beige: {
    DEFAULT: '#eff6e0',
    100: '#384915',
    200: '#71912a',
    300: '#a4cc4e',
    400: '#c9e197',
    500: '#eff6e0',
    600: '#f2f8e6',
    700: '#f5f9ec',
    800: '#f8fbf2',
    900: '#fcfdf9'
  }
};

// Ant Design theme configuration
export const theme: ThemeConfig = {
  token: {
    // Primary colors
    colorPrimary: colors.midnight_green[600],
    colorSuccess: colors.ash_gray[500],
    colorWarning: colors.beige[300],
    colorError: '#ef4444',
    colorInfo: colors.air_force_blue[600],
    
    // Background colors
    colorBgBase: colors.rich_black[500],
    colorBgContainer: colors.rich_black[400],
    colorBgElevated: colors.midnight_green[500],
    colorBgLayout: colors.rich_black[500],
    
    // Border colors
    colorBorder: colors.midnight_green[400],
    colorBorderSecondary: colors.air_force_blue[200],
    
    // Text colors
    colorText: colors.beige[500],
    colorTextSecondary: colors.ash_gray[500],
    colorTextTertiary: colors.air_force_blue[800],
    colorTextQuaternary: colors.air_force_blue[600],
    
    // Border radius
    borderRadius: 12,
    borderRadiusLG: 16,
    borderRadiusSM: 8,
    
    // Font
    fontSize: 14,
    fontSizeHeading1: 38,
    fontSizeHeading2: 30,
    fontSizeHeading3: 24,
    fontSizeHeading4: 20,
    fontSizeHeading5: 16,
    
    // Spacing
    padding: 16,
    paddingLG: 24,
    paddingSM: 12,
    paddingXS: 8,
    
    // Box shadow
    boxShadow: '0 4px 20px rgba(1, 22, 30, 0.4)',
    boxShadowSecondary: '0 2px 8px rgba(1, 22, 30, 0.3)',
  },
  components: {
    Button: {
      colorPrimary: colors.midnight_green[600],
      colorPrimaryHover: colors.midnight_green[700],
      colorPrimaryActive: colors.midnight_green[800],
      primaryShadow: '0 4px 12px rgba(32, 121, 156, 0.3)',
    },
    Card: {
      colorBgContainer: colors.midnight_green[500],
      colorBorderSecondary: colors.air_force_blue[300],
      boxShadowTertiary: '0 6px 24px rgba(1, 22, 30, 0.5)',
    },
    Table: {
      colorBgContainer: colors.midnight_green[400],
      colorBorderSecondary: colors.air_force_blue[300],
      headerBg: colors.midnight_green[300],
      rowHoverBg: colors.midnight_green[600],
    },
    Tabs: {
      colorBorderSecondary: colors.air_force_blue[400],
      itemActiveColor: colors.rich_black[800],
      itemHoverColor: colors.air_force_blue[700],
      itemSelectedColor: colors.rich_black[800],
    },
    Input: {
      colorBgContainer: colors.rich_black[400],
      colorBorder: colors.air_force_blue[300],
      colorTextPlaceholder: colors.air_force_blue[600],
      activeBorderColor: colors.midnight_green[700],
      hoverBorderColor: colors.air_force_blue[500],
    },
    Select: {
      colorBgContainer: colors.rich_black[400],
      colorBorder: colors.air_force_blue[300],
      optionSelectedBg: colors.midnight_green[600],
    },
    Modal: {
      contentBg: colors.midnight_green[400],
      headerBg: 'transparent',
      titleFontSize: 16,
    },
    Tag: {
      defaultBg: colors.air_force_blue[300],
      defaultColor: colors.beige[500],
    },
    Badge: {
      colorSuccess: colors.ash_gray[500],
      colorError: '#ef4444',
      colorWarning: colors.beige[300],
    },
    Statistic: {
      contentFontSize: 28,
      titleFontSize: 14,
    },
  },
};

export default theme;





