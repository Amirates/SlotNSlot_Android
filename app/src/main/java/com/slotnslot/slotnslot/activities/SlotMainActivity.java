package com.slotnslot.slotnslot.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.TabLayout;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.jakewharton.rxbinding2.view.RxView;
import com.slotnslot.slotnslot.R;
import com.slotnslot.slotnslot.SlotType;
import com.slotnslot.slotnslot.adapters.TabPagerAdapter;
import com.slotnslot.slotnslot.geth.GethManager;
import com.slotnslot.slotnslot.geth.Utils;
import com.slotnslot.slotnslot.models.AccountViewModel;
import com.slotnslot.slotnslot.provider.AccountProvider;
import com.slotnslot.slotnslot.provider.RxSlotRoom;
import com.slotnslot.slotnslot.provider.RxSlotRooms;
import com.slotnslot.slotnslot.utils.Constants;
import com.slotnslot.slotnslot.utils.Convert;
import com.slotnslot.slotnslot.utils.SlotUtil;

import org.ethereum.geth.Node;

import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class SlotMainActivity extends SlotRootActivity {
    public static final String TAG = SlotMainActivity.class.getSimpleName();

    @BindView(R.id.slot_tab_layout)
    TabLayout tabLayout;
    @BindView(R.id.slot_viewpager)
    ViewPager viewPager;
    @BindView(R.id.nav_address_textview)
    TextView addressTextView;
    @BindView(R.id.nav_address_more_button)
    ImageButton moreButton;
    @BindView(R.id.nav_amount_textview)
    TextView amountTextView;
    @BindView(R.id.global_loading_container)
    RelativeLayout loadingView;
    @BindView(R.id.nav_bio_textview)
    TextView peerCount;

    private AccountViewModel accountViewModel;
    private boolean doubleBackToExitPressedOnce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_slot_main);
        ButterKnife.bind(this);

        setLoadingView(loadingView);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        tabLayout.addTab(tabLayout.newTab().setText("PLAY"));
        tabLayout.addTab(tabLayout.newTab().setText("MAKE"));
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            View tab = ((ViewGroup) tabLayout.getChildAt(0)).getChildAt(i);
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) tab.getLayoutParams();
            int space = SlotUtil.convertDpToPixel(10.5f, this);
            p.setMargins(space, 0, space, 0);
            tab.requestLayout();
        }

        // Creating TabPagerAdapter adapter
        TabPagerAdapter pagerAdapter = new TabPagerAdapter(getSupportFragmentManager(), tabLayout.getTabCount());
        viewPager.setAdapter(pagerAdapter);
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));

        // Set TabSelectedListener
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });

        RxView.clicks(moreButton).subscribe(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("text", addressTextView.getText());
            clipboard.setPrimaryClip(clip);

            Utils.showToast("Wallet address copied.");
        });

//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(view -> Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                .setAction("Action", null).show());

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                AccountProvider.updateBalance();
            }
        };
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        setAccountModel();
        RxSlotRooms.init();
        continuePlaying();
        showPeerCount();
    }

    private void showPeerCount() {
        Observable.interval(1, TimeUnit.SECONDS)
                .compose(bindToLifecycle())
                .map(n -> {
                    Node node = GethManager.getNode();
                    return node.getPeersInfo().size();
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(size -> peerCount.setText("peer count : " + size), Throwable::printStackTrace);
    }

    private void continuePlaying() {
        RxSlotRooms
                .rxSlotRoomMapSubject
                .debounce(2, TimeUnit.SECONDS)
                .take(1)
                .subscribe(slotMap -> {
                    for (RxSlotRoom rxSlotRoom : slotMap.values()) {
                        if (AccountProvider.identical(rxSlotRoom.getSlotRoom().getPlayerAddress())) {
                            rxSlotRoom.getMachine().mPlayer().subscribe(address -> {
                                if (AccountProvider.identical(address.toString())) {
                                    Intent intent = new Intent(getApplicationContext(), SlotGameActivity.class);
                                    Bundle bundle = new Bundle();
                                    bundle.putSerializable(Constants.ACTIVITY_EXTRA_KEY_SLOT_TYPE, SlotType.PLAYER);
                                    bundle.putSerializable(Constants.BUNDLE_KEY_SLOT_ROOM, rxSlotRoom.getSlotAddress());
                                    intent.putExtras(bundle);
                                    startActivity(intent);
                                }
                            }, Throwable::printStackTrace);
                            break;
                        }
                    }
                }, Throwable::printStackTrace);
    }

    private void setAccountModel() {
        accountViewModel = new AccountViewModel(AccountProvider.accountSubject);

        accountViewModel.balance
                .subscribe(bigInteger -> amountTextView.setText(Convert.fromWei(bigInteger, Convert.Unit.ETHER) + " ETH"));
        accountViewModel.addressHex
                .subscribe(hex -> addressTextView.setText(hex));

        AccountProvider.updateBalance();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            if (doubleBackToExitPressedOnce) {
                super.onBackPressed();
                return;
            }
            this.doubleBackToExitPressedOnce = true;
            Utils.showToast("press back again to exit");
            new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 1000);
        }
    }

    @OnClick(R.id.nav_wallet_layout)
    public void showWallet() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        Intent intent = new Intent(getApplicationContext(), MyPageActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RxSlotRooms.destroy();
        GethManager.getInstance().stopNode();
    }
}
