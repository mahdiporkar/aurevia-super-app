import React from 'react';
import {Card,Tabs,Typography} from 'antd';

export interface AdminTab {
  key:string;
  label:string;
  children:React.ReactNode;
}

export interface AdminTabbedPageProps {
  title:string;
  description:string;
  /** Already filtered to what this subject may see; the component adds no policy. */
  tabs:readonly AdminTab[];
  activeKey:string;
  onChange:(key:string)=>void;
}

/**
 * One header and one tab strip for an administrative domain. Deliberately free of
 * domain logic: the caller decides which tabs exist, which is active and what a
 * tab change means for the URL.
 */
export function AdminTabbedPage({title,description,tabs,activeKey,onChange}:AdminTabbedPageProps) {
  return <Card>
    <Typography.Title level={3} style={{marginBottom:4}}>{title}</Typography.Title>
    <Typography.Text type="secondary">{description}</Typography.Text>
    <Tabs
      activeKey={activeKey}
      onChange={onChange}
      items={tabs.map(tab=>({key:tab.key,label:tab.label,children:tab.children}))}
      // Several domains carry six tabs. Ant Design's line tabs scroll and collapse
      // into an overflow menu on their own, so a narrow viewport keeps the layout.
      tabBarStyle={{marginTop:16}}
      tabBarGutter={16}
      style={{marginTop:4}}
    />
  </Card>;
}
