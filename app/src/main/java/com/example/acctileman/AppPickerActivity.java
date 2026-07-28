package com.example.acctileman;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用选择界面：列出已安装的第三方应用，选择后返回包名和可选的无障碍服务。
 * 传入参数:
 *   "slot_index" - int, 当前配置的 slot 编号
 * 返回结果:
 *   "package_name" - 选中 app 的包名
 *   "service_component" - 选中无障碍服务的完整组件名（如果有的话）
 *   "label" - 选中 app 的名称（作为默认磁贴标签）
 */
public class AppPickerActivity extends Activity {

    public static final String EXTRA_SLOT_INDEX = "slot_index";
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String EXTRA_SERVICE_COMPONENT = "service_component";
    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_DEEP_LINK = "deep_link";

    private AppListAdapter adapter;
    private ListView listView;
    private SearchView searchView;
    private TextView tvEmpty;
    private boolean loading = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.d("AppPicker", "onCreate");
        setTitle("选择应用");

        // 构建布局
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        // 搜索栏
        searchView = new SearchView(this);
        searchView.setIconifiedByDefault(false);
        searchView.setQueryHint("搜索应用...");
        searchView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(searchView);

        // 列表
        listView = new ListView(this);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(listView);

        // 空提示
        tvEmpty = new TextView(this);
        tvEmpty.setText("正在加载应用列表...");
        tvEmpty.setTextSize(16);
        tvEmpty.setGravity(android.view.Gravity.CENTER);
        listView.setEmptyView(tvEmpty);
        root.addView(tvEmpty);

        setContentView(root);

        // 加载应用列表（后台线程）
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfoHelper.AppItem> apps = AppInfoHelper.getInstalledApps(AppPickerActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loading = false;
                        adapter = new AppListAdapter(apps);
                        listView.setAdapter(adapter);
                        tvEmpty.setText("未找到应用");
                        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                            @Override
                            public boolean onQueryTextSubmit(String query) {
                                return false;
                            }

                            @Override
                            public boolean onQueryTextChange(String newText) {
                                adapter.getFilter().filter(newText);
                                return true;
                            }
                        });
                    }
                });
            }
        }).start();

        // 点击选择
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                AppInfoHelper.AppItem item = (AppInfoHelper.AppItem) adapter.getItem(position);
                Logger.d("AppPicker", "选中: " + item.appName + " (" + item.packageName + ")");
                onAppSelected(item);
            }
        });
    }

    private void onAppSelected(final AppInfoHelper.AppItem item) {
        // 检查该 app 是否有无障碍服务
        final List<AppInfoHelper.ServiceItem> services = AppInfoHelper.getAccessibilityServices(this, item.packageName);

        if (services.isEmpty()) {
            // 没有无障碍服务，直接返回包名
            Toast.makeText(this,
                    item.appName + " 未检测到无障碍服务\n已自动填入包名",
                    Toast.LENGTH_SHORT).show();
            // 仍然检查 Deep Link
            pickDeepLink(item, "", "");
            return;
        }

        if (services.size() == 1) {
            // 只有一个服务，直接选中
            Logger.d("AppPicker", "自动选择唯一服务: " + services.get(0).component);
            pickDeepLink(item, services.get(0).component, "");
            return;
        }

        // 多个服务，弹窗让用户选择
        ServicePickerDialog dialog = new ServicePickerDialog(this, item, services);
        dialog.setListener(new ServicePickerDialog.OnServiceSelectedListener() {
            @Override
            public void onSelected(AppInfoHelper.ServiceItem service) {
                Logger.d("AppPicker", "用户选择服务: " + service.component);
                pickDeepLink(item, service.component, "");
            }

            @Override
            public void onCancelled() {
                // 用户取消服务选择，仍然返回包名（不带服务）
                Logger.d("AppPicker", "用户取消服务选择，仅返回包名");
                pickDeepLink(item, "", "");
            }
        });
        dialog.show();
    }

    /** 选择 Deep Link（如果有的话），然后返回结果 */
    private void pickDeepLink(final AppInfoHelper.AppItem item,
                              final String serviceComponent,
                              final String defaultDeepLink) {
        // 后台线程读取 Deep Link
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<AppInfoHelper.DeepLinkItem> links =
                        AppInfoHelper.getDeepLinks(AppPickerActivity.this, item.packageName);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (links.isEmpty()) {
                            // 没有可启动入口，提示用户后返回
                            Toast.makeText(AppPickerActivity.this,
                                    item.appName + " 未检测到可启动入口\n已自动填入包名和服务",
                                    Toast.LENGTH_SHORT).show();
                            returnResult(item.packageName, serviceComponent, item.appName, "");
                            return;
                        }

                        if (links.size() == 1) {
                            // 只有一个 Deep Link，直接返回
                            Logger.d("AppPicker", "自动选择唯一 Deep Link: " + links.get(0).uri);
                            returnResult(item.packageName, serviceComponent, item.appName, links.get(0).uri);
                            return;
                        }

                        // 多个 Deep Link，弹窗选择
                        DeepLinkPickerDialog dialog = new DeepLinkPickerDialog(
                                AppPickerActivity.this, item, links);
                        dialog.setListener(new DeepLinkPickerDialog.OnDeepLinkSelectedListener() {
                            @Override
                            public void onSelected(AppInfoHelper.DeepLinkItem link) {
                                Logger.d("AppPicker", "用户选择 Deep Link: " + link.uri);
                                returnResult(item.packageName, serviceComponent, item.appName, link.uri);
                            }

                            @Override
                            public void onCancelled() {
                                Logger.d("AppPicker", "用户跳过 Deep Link 选择");
                                returnResult(item.packageName, serviceComponent, item.appName, "");
                            }
                        });
                        dialog.show();
                    }
                });
            }
        }).start();
    }

    private void returnResult(String packageName, String serviceComponent, String label, String deepLink) {
        Intent data = new Intent();
        data.putExtra(EXTRA_PACKAGE_NAME, packageName);
        data.putExtra(EXTRA_SERVICE_COMPONENT, serviceComponent);
        data.putExtra(EXTRA_LABEL, label);
        data.putExtra(EXTRA_DEEP_LINK, deepLink);
        setResult(RESULT_OK, data);
        finish();
    }

    // ---- Adapter ----

    private static class AppListAdapter extends BaseAdapter implements Filterable {
        private List<AppInfoHelper.AppItem> originalList;
        private List<AppInfoHelper.AppItem> filteredList;

        AppListAdapter(List<AppInfoHelper.AppItem> items) {
            this.originalList = items;
            this.filteredList = new ArrayList<>(items);
        }

        @Override
        public int getCount() {
            return filteredList.size();
        }

        @Override
        public Object getItem(int position) {
            return filteredList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = createItemView(parent);
            }
            bindItemView(convertView, filteredList.get(position));
            return convertView;
        }

        private View createItemView(ViewGroup parent) {
            // 使用代码创建 item 布局（避免额外 XML）
            LinearLayout layout = new LinearLayout(parent.getContext());
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setPadding(16, 12, 16, 12);
            layout.setMinimumHeight(80);

            ImageView iv = new ImageView(parent.getContext());
            iv.setId(android.R.id.icon);
            int iconSize = (int) (48 * parent.getContext().getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconLp.setMarginEnd(16);
            iv.setLayoutParams(iconLp);
            layout.addView(iv);

            LinearLayout textContainer = new LinearLayout(parent.getContext());
            textContainer.setOrientation(LinearLayout.VERTICAL);
            textContainer.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(parent.getContext());
            tvName.setId(android.R.id.text1);
            tvName.setTextSize(16);
            tvName.setTextColor(0xFF000000);
            textContainer.addView(tvName);

            TextView tvPkg = new TextView(parent.getContext());
            tvPkg.setId(android.R.id.text2);
            tvPkg.setTextSize(12);
            tvPkg.setTextColor(0xFF888888);
            textContainer.addView(tvPkg);

            layout.addView(textContainer);
            return layout;
        }

        private void bindItemView(View view, AppInfoHelper.AppItem item) {
            ImageView iv = view.findViewById(android.R.id.icon);
            TextView tvName = view.findViewById(android.R.id.text1);
            TextView tvPkg = view.findViewById(android.R.id.text2);

            if (iv != null && item.icon != null) {
                iv.setImageDrawable(item.icon);
            }
            if (tvName != null) {
                tvName.setText(item.appName);
            }
            if (tvPkg != null) {
                tvPkg.setText(item.packageName);
            }
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    String query = constraint != null ? constraint.toString().toLowerCase().trim() : "";
                    if (query.isEmpty()) {
                        results.values = new ArrayList<>(originalList);
                        results.count = originalList.size();
                    } else {
                        List<AppInfoHelper.AppItem> filtered = new ArrayList<>();
                        for (AppInfoHelper.AppItem item : originalList) {
                            if (item.appName.toLowerCase().contains(query)
                                    || item.packageName.toLowerCase().contains(query)) {
                                filtered.add(item);
                            }
                        }
                        results.values = filtered;
                        results.count = filtered.size();
                    }
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filteredList = (List<AppInfoHelper.AppItem>) results.values;
                    notifyDataSetChanged();
                }
            };
        }
    }
}
