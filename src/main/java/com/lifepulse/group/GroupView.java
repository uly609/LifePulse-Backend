package com.lifepulse.group;
import com.lifepulse.entity.GroupActivity;
import com.lifepulse.entity.Shop;
import com.lifepulse.entity.GroupTeam;
import com.lifepulse.entity.Voucher;
import java.util.List;
public record GroupView(GroupActivity activity, Voucher voucher, Shop shop, List<GroupTeam> openGroups,
                        GroupEligibility eligibility, int remainingSlots) {}
