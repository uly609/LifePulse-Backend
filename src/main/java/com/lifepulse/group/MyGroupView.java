package com.lifepulse.group;
import com.lifepulse.entity.GroupActivity;
import com.lifepulse.entity.GroupMember;
import com.lifepulse.entity.GroupTeam;
public record MyGroupView(GroupActivity activity, GroupTeam group, GroupMember member) {}
