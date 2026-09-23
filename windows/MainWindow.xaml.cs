using System.IO;
using System.Net.Http;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;
using System.Diagnostics;
namespace Checkit;
public record Stop(string id, string name, string? timeZone = null);
public record Line(string label, string? color, string? textColor);
public record DepartureTime(string? scheduled, string? realtime, int delayMinutes);
public record Departure(string id, Line line, string destination, DepartureTime time, bool realtime, bool cancelled);
public record Snapshot(Stop station, DateTimeOffset generatedAt, List<Departure> departures);
public record Alarm(string id, string line, string destination, string stopId, string stopName, DateTimeOffset fireAt, int minutesBefore, string? timeZone = null);
public record Preferences(Stop Stop, bool Pinned, double Width, double Height, double Left, double Top, List<Stop>? Favorites);
public partial class MainWindow : Window {
    const string Api = "https://checkit-omega-two.vercel.app";
    static readonly HttpClient Client = new() { Timeout = TimeSpan.FromSeconds(18) };
    static readonly string DataDirectory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Checkit");
    static readonly string AlarmsFile = Path.Combine(DataDirectory, "alarms.json");
    readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromSeconds(60) };
    readonly DispatcherTimer countdownTimer = new() { Interval = TimeSpan.FromSeconds(1) };
    Stop stop = new("de-DELFI_de:04011:13927_G", "Bremen Hauptbahnhof");
    Snapshot? snapshot;
    bool busy, stale;
    long revision;
    List<Stop> favorites = new();
    List<Alarm> alarms = new();
    public MainWindow() {
        InitializeComponent();
        try {
            var preferences = JsonSerializer.Deserialize<Preferences>(File.ReadAllText(Path.Combine(DataDirectory,"settings.json")));
            if(preferences is not null) { stop=preferences.Stop;Topmost=preferences.Pinned;Width=Math.Clamp(preferences.Width,300,1200);Height=Math.Clamp(preferences.Height,210,1000);Left=Math.Clamp(preferences.Left,SystemParameters.VirtualScreenLeft,Math.Max(SystemParameters.VirtualScreenLeft,SystemParameters.VirtualScreenLeft+SystemParameters.VirtualScreenWidth-Width));Top=Math.Clamp(preferences.Top,SystemParameters.VirtualScreenTop,Math.Max(SystemParameters.VirtualScreenTop,SystemParameters.VirtualScreenTop+SystemParameters.VirtualScreenHeight-Height)); }
            snapshot = JsonSerializer.Deserialize<Snapshot>(File.ReadAllText(Path.Combine(DataDirectory,"snapshot.json")));
            if(snapshot?.station.id!=stop.id)snapshot=null;
            stale=true;
        } catch { }
        favorites = LoadFavorites();
        try {
            var savedAlarms = JsonSerializer.Deserialize<List<Alarm>>(File.ReadAllText(AlarmsFile));
            if(savedAlarms is not null) alarms = savedAlarms.Where(a=>a.fireAt>DateTimeOffset.UtcNow).ToList();
        } catch { }
        timer.Tick += async (_,_)=> await Refresh();
        countdownTimer.Tick += (_,_)=> { Render(); CheckAlarms(); };
    }
    List<Stop> LoadFavorites() {
        try {
            var preferences = JsonSerializer.Deserialize<Preferences>(File.ReadAllText(Path.Combine(DataDirectory,"settings.json")));
            var saved = preferences?.Favorites?.Where(s=>s is not null&&!string.IsNullOrWhiteSpace(s.id)&&!string.IsNullOrWhiteSpace(s.name)).ToList();
            if(saved is not null)return saved;
        } catch { }
        return new List<Stop>{ stop };
    }
    async void OnLoaded(object sender,RoutedEventArgs e) { Pin.Content=Topmost?"◆":"◇";Render();RenderFavorites();RenderAlarms();timer.Start();countdownTimer.Start();await Refresh(); }
    async Task Refresh() {
        if(busy)return;
        busy=true;RefreshButton.IsEnabled=false;
        var requested=stop;var version=revision;
        if(snapshot is null)Status.Text="Verbinde …";
        try {
            var json=await Client.GetStringAsync($"{Api}/api/departures?stopId={Uri.EscapeDataString(requested.id)}&stopName={Uri.EscapeDataString(requested.name)}");
            var data=JsonSerializer.Deserialize<Snapshot>(json)??throw new Exception();
            if(version==revision) {
                snapshot=data;stale=false;
                try { Directory.CreateDirectory(DataDirectory);File.WriteAllText(Path.Combine(DataDirectory,"snapshot.json"),json); } catch { }
            }
        } catch { if(version==revision)stale=true; }
        finally { busy=false;RefreshButton.IsEnabled=true;Render(); }
        if(version!=revision)await Refresh();
    }
    static TimeZoneInfo ZoneFor(string? name) {
        try { return string.IsNullOrWhiteSpace(name)?TimeZoneInfo.Local:TimeZoneInfo.FindSystemTimeZoneById(name); }
        catch { return TimeZoneInfo.Local; }
    }
    static string Clock(DateTimeOffset value,string? timeZone)=>TimeZoneInfo.ConvertTime(value,ZoneFor(timeZone)).ToString("HH:mm");
    static string Clock(string? value,string? timeZone)=>DateTimeOffset.TryParse(value,out var parsed)?Clock(parsed,timeZone):"—";
    static string Remaining(string? value) => DateTimeOffset.TryParse(value,out var parsed) ? (parsed <= DateTimeOffset.UtcNow ? "jetzt" : $"{Math.Ceiling((parsed-DateTimeOffset.UtcNow).TotalMinutes)} min") : "—";
    static Brush Color(string? value, string fallback) { try { return (Brush)new BrushConverter().ConvertFromString(value??fallback)!; }catch{return (Brush)new BrushConverter().ConvertFromString(fallback)!;} }
    void Render() {
        if(Rows is null)return;
        Station.Text=stop.name;Rows.Children.Clear();
        var aged=snapshot is not null&&(DateTimeOffset.UtcNow-snapshot.generatedAt).TotalMinutes>2;
        Status.Text=snapshot is null?"OFFLINE · Bitte aktualisieren":$"{(stale||aged?"VERALTET":snapshot.departures.Any(d=>d.realtime)?"ECHTZEIT":"FAHRPLAN")} · Stand {Clock(snapshot.generatedAt,snapshot.station.timeZone)}";
        Status.ToolTip=snapshot?.station.timeZone is null?"Uhrzeiten: Gerätezeit":"Uhrzeiten: Ortszeit der Haltestelle";
        var count=Math.Clamp((int)(Rows.ActualHeight/42),0,12);
        var items=snapshot?.departures.Where(d=>DateTimeOffset.TryParse(d.time.realtime??d.time.scheduled,out var date)&&date>DateTimeOffset.UtcNow.AddMinutes(-1)).Take(count).ToList();
        if(items is null||items.Count==0){if(count>0)Rows.Children.Add(new TextBlock{Text=snapshot is null?"Verbindung prüfen und ↻ drücken.":"Keine aktuellen Abfahrten.",FontSize=12,Foreground=Color(null,"#A8ADB5"),Margin=new Thickness(0,8,0,0)});return;}
        foreach(var d in items) {
            var row=new Grid{Height=42};row.ColumnDefinitions.Add(new(){Width=new GridLength(43)});row.ColumnDefinitions.Add(new(){Width=new GridLength(1,GridUnitType.Star)});row.ColumnDefinitions.Add(new(){Width=new GridLength(68)});row.ColumnDefinitions.Add(new(){Width=new GridLength(30)});
            var badge=new Border{Background=Color(d.line.color,"#FFB51B"),CornerRadius=new CornerRadius(2),Padding=new Thickness(3),VerticalAlignment=VerticalAlignment.Center,Margin=new Thickness(0,0,7,0),Child=new TextBlock{Text=d.line.label,Foreground=Color(d.line.textColor,"#111317"),FontWeight=FontWeights.Bold,FontSize=12,TextAlignment=TextAlignment.Center,TextTrimming=TextTrimming.CharacterEllipsis}};
            row.Children.Add(badge);
            var destination=new StackPanel{VerticalAlignment=VerticalAlignment.Center,Margin=new Thickness(3,0,8,0)};
            destination.Children.Add(new TextBlock{Text=d.destination,TextTrimming=TextTrimming.CharacterEllipsis,FontSize=14,ToolTip=d.destination});
            if(d.time.delayMinutes>0)destination.Children.Add(new TextBlock{Text=$"+{d.time.delayMinutes} min",FontSize=9,Foreground=Color(null,"#FFB51B")});
            Grid.SetColumn(destination,1);row.Children.Add(destination);
            var time=new StackPanel{VerticalAlignment=VerticalAlignment.Center};
            time.Children.Add(new TextBlock{Text=d.cancelled?"AUS":Remaining(d.time.realtime??d.time.scheduled),Foreground=Color(null,"#FFB51B"),FontSize=17,FontWeight=FontWeights.SemiBold,TextAlignment=TextAlignment.Right});
            time.Children.Add(new TextBlock{Text=Clock(d.time.realtime??d.time.scheduled,snapshot?.station.timeZone),Foreground=Color(null,"#A8ADB5"),FontSize=9,TextAlignment=TextAlignment.Right});
            Grid.SetColumn(time,2);row.Children.Add(time);
            var alarmActive=alarms.Any(a=>a.id==d.id);
            var alarmButton=new Button{Content="⏰",Tag=d,Foreground=Color(null,alarmActive?"#FFB51B":"#596266"),FontSize=13,Padding=new Thickness(2),Margin=new Thickness(4,0,0,0),ToolTip=alarmActive?"Erinnerung aktiv – klicken zum Löschen":"An diese Abfahrt erinnern lassen"};
            alarmButton.Click+=AlarmClick;
            Grid.SetColumn(alarmButton,3);row.Children.Add(alarmButton);Rows.Children.Add(row);
        }
    }
    async void Search(object sender,RoutedEventArgs e) {
        var q=Query.Text.Trim();if(q.Length==0)return;
        SearchButton.IsEnabled=false;
        try { using var json=JsonDocument.Parse(await Client.GetStringAsync($"{Api}/api/stops/search?q={Uri.EscapeDataString(q)}"));Results.ItemsSource=JsonSerializer.Deserialize<List<Stop>>(json.RootElement.GetProperty("results").GetRawText());Status.Text=Results.Items.Count==0?"Keine Haltestelle gefunden.":"Haltestelle auswählen"; }
        catch { Status.Text="Suche fehlgeschlagen. Bitte erneut versuchen."; }
        finally {SearchButton.IsEnabled=true;}
    }
    async void SelectStop(object sender,SelectionChangedEventArgs e) {if(Results.SelectedItem is not Stop chosen)return;stop=chosen;revision++;snapshot=null;Settings.Visibility=Visibility.Collapsed;Render();SavePreferences();await Refresh();}
    void SaveFavorite(object sender,RoutedEventArgs e) {
        if(!favorites.Any(f=>f.id==stop.id)){favorites.Add(stop);SavePreferences();RenderFavorites();}
    }
    void RenderFavorites() {
        if(FavoritesPanel is null)return;
        FavoritesPanel.Children.Clear();
        foreach(var fav in favorites) {
            var dock=new DockPanel{Margin=new Thickness(0,3,0,0)};
            var remove=new Button{Content="✕",ToolTip="Favorit entfernen",Foreground=Color(null,"#A8ADB5")};
            DockPanel.SetDock(remove,Dock.Right);
            var id=fav.id;
            remove.Click+=(_,_)=>{favorites.RemoveAll(f=>f.id==id);SavePreferences();RenderFavorites();};
            var pick=new Button{Content=fav.name,HorizontalContentAlignment=HorizontalAlignment.Left,ToolTip=fav.name};
            var chosen=fav;
            pick.Click+=async(_,_)=>{stop=chosen;revision++;snapshot=null;Settings.Visibility=Visibility.Collapsed;Render();SavePreferences();await Refresh();};
            dock.Children.Add(remove);dock.Children.Add(pick);
            FavoritesPanel.Children.Add(dock);
        }
        if(favorites.Count==0)FavoritesPanel.Children.Add(new TextBlock{Text="Noch keine Favoriten.",FontSize=11,Foreground=Color(null,"#A8ADB5")});
    }
    void RenderAlarms() {
        if(AlarmsPanel is null)return;
        AlarmsPanel.Children.Clear();
        foreach(var alarm in alarms.OrderBy(a=>a.fireAt)) {
            var dock=new DockPanel{Margin=new Thickness(0,3,0,0)};
            var remove=new Button{Content="✕",ToolTip="Erinnerung löschen",Foreground=Color(null,"#A8ADB5")};
            DockPanel.SetDock(remove,Dock.Right);
            var id=alarm.id;
            remove.Click+=(_,_)=>{alarms.RemoveAll(a=>a.id==id);SaveAlarms();Render();RenderAlarms();};
            dock.Children.Add(remove);
            var local=Clock(alarm.fireAt,alarm.timeZone);
            dock.Children.Add(new TextBlock{Text=$"⏰ Linie {alarm.line} nach {alarm.destination} · {local} Uhr",FontSize=11,VerticalAlignment=VerticalAlignment.Center,TextTrimming=TextTrimming.CharacterEllipsis,ToolTip=$"{alarm.stopName} · {alarm.minutesBefore} Min. vorher"});
            AlarmsPanel.Children.Add(dock);
        }
        if(alarms.Count==0)AlarmsPanel.Children.Add(new TextBlock{Text="Keine aktiven Erinnerungen.",FontSize=11,Foreground=Color(null,"#A8ADB5")});
    }
    void AlarmClick(object sender,RoutedEventArgs e) {
        if(sender is not Button button||button.Tag is not Departure dep)return;
        var existing=alarms.FirstOrDefault(a=>a.id==dep.id);
        if(existing is not null){alarms.Remove(existing);SaveAlarms();Render();RenderAlarms();Status.Text="⏰ Erinnerung gelöscht.";return;}
        var menu=new ContextMenu{PlacementTarget=button};
        foreach(var minutes in new[]{5,10,15}) {
            var item=new MenuItem{Header=$"In {minutes} Minuten erinnern"};
            var m=minutes;
            item.Click+=(_,_)=>SetAlarm(dep,m);
            menu.Items.Add(item);
        }
        menu.IsOpen=true;
    }
    void SetAlarm(Departure dep,int minutesBefore) {
        if(!DateTimeOffset.TryParse(dep.time.realtime??dep.time.scheduled,out var departureAt)){Status.Text="Für diese Abfahrt ist keine Erinnerung möglich.";return;}
        var fireAt=departureAt.AddMinutes(-minutesBefore);
        if(fireAt<=DateTimeOffset.UtcNow.AddSeconds(20)){Status.Text="Dafür ist es zu spät – die Abfahrt steht kurz bevor.";return;}
        alarms.RemoveAll(a=>a.id==dep.id);
        alarms.Add(new Alarm(dep.id,dep.line.label,dep.destination,stop.id,stop.name,fireAt,minutesBefore,snapshot?.station.timeZone));
        SaveAlarms();Render();RenderAlarms();
        Status.Text=$"⏰ Erinnerung aktiv: {dep.line.label} in {minutesBefore} Minuten.";
    }
    void CheckAlarms() {
        var due=alarms.Where(a=>a.fireAt<=DateTimeOffset.UtcNow).ToList();
        foreach(var alarm in due){alarms.Remove(alarm);ShowAlarmPopup(alarm);}
        if(due.Count>0){SaveAlarms();Render();RenderAlarms();}
    }
    void ShowAlarmPopup(Alarm alarm) {
        var popup=new Window{
            Title="Checkit ⏰",Width=330,SizeToContent=SizeToContent.Height,WindowStyle=WindowStyle.None,
            ResizeMode=ResizeMode.NoResize,Topmost=true,ShowInTaskbar=false,WindowStartupLocation=WindowStartupLocation.Manual,
            Background=new SolidColorBrush((Color)ColorConverter.ConvertFromString("#252829"))
        };
        var work=SystemParameters.WorkArea;
        popup.Left=work.Right-340;popup.Top=work.Bottom-190;
        var panel=new StackPanel{Margin=new Thickness(14)};
        panel.Children.Add(new TextBlock{Text="⏰ Abfahrt",Foreground=Brushes.White,FontWeight=FontWeights.SemiBold,FontSize=15});
        panel.Children.Add(new TextBlock{Text=$"Linie {alarm.line} nach {alarm.destination} fährt in {alarm.minutesBefore} Minuten ab.",Foreground=Brushes.White,FontSize=13,TextWrapping=TextWrapping.Wrap,Margin=new Thickness(0,6,0,2)});
        panel.Children.Add(new TextBlock{Text=alarm.stopName,Foreground=Color(null,"#A8ADB5"),FontSize=11});
        var ok=new Button{Content="OK",Margin=new Thickness(0,10,0,0),HorizontalAlignment=HorizontalAlignment.Right};
        ok.Click+=(_,_)=>popup.Close();
        panel.Children.Add(ok);
        popup.Content=new Border{BorderBrush=Color(null,"#FFB51B"),BorderThickness=new Thickness(1),Child=panel};
        var autoClose=new DispatcherTimer{Interval=TimeSpan.FromSeconds(90)};
        autoClose.Tick+=(_,_)=>{autoClose.Stop();popup.Close();};
        autoClose.Start();
        try{System.Media.SystemSounds.Exclamation.Play();}catch{}
        popup.Show();
    }
    void SaveAlarms(){try{Directory.CreateDirectory(DataDirectory);File.WriteAllText(AlarmsFile,JsonSerializer.Serialize(alarms));}catch{}}
    void QueryKeyDown(object sender,KeyEventArgs e){if(e.Key==Key.Enter&&SearchButton.IsEnabled)Search(sender,e);}
    void ToggleSettings(object sender,RoutedEventArgs e){Settings.Visibility=Settings.Visibility==Visibility.Visible?Visibility.Collapsed:Visibility.Visible;if(Settings.Visibility==Visibility.Visible){Height=Math.Max(Height,560);Query.Focus();}Dispatcher.BeginInvoke(Render,DispatcherPriority.Loaded);}
    void TogglePin(object sender,RoutedEventArgs e){Topmost=!Topmost;Pin.Content=Topmost?"◆":"◇";SavePreferences();}
    void DragHeader(object sender,MouseButtonEventArgs e){if(e.OriginalSource is TextBlock)DragMove();}
    void Minimize(object sender,RoutedEventArgs e)=>WindowState=WindowState.Minimized;
    void CloseWindow(object sender,RoutedEventArgs e)=>Close();
    async void ManualRefresh(object sender,RoutedEventArgs e)=>await Refresh();
    void OnSizeChanged(object sender,SizeChangedEventArgs e)=>Dispatcher.BeginInvoke(Render,DispatcherPriority.Loaded);
    void SavePreferences(){try{Directory.CreateDirectory(DataDirectory);File.WriteAllText(Path.Combine(DataDirectory,"settings.json"),JsonSerializer.Serialize(new Preferences(stop,Topmost,Width,Height,Left,Top,favorites)));}catch{}}
    void OnClosing(object? sender,System.ComponentModel.CancelEventArgs e){timer.Stop();countdownTimer.Stop();SavePreferences();SaveAlarms();}
    void OpenLink(object sender,System.Windows.Navigation.RequestNavigateEventArgs e){Process.Start(new ProcessStartInfo(e.Uri.AbsoluteUri){UseShellExecute=true});e.Handled=true;}
}
