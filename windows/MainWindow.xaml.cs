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
public record Stop(string id, string name);
public record Line(string label, string? color, string? textColor);
public record DepartureTime(string? scheduled, string? realtime, int delayMinutes);
public record Departure(string id, Line line, string destination, DepartureTime time, bool realtime, bool cancelled);
public record Snapshot(Stop station, DateTimeOffset generatedAt, List<Departure> departures);
public record Preferences(Stop Stop, bool Pinned, double Width, double Height, double Left, double Top);
public partial class MainWindow : Window {
    const string Api = "https://checkit-omega-two.vercel.app";
    static readonly HttpClient Client = new() { Timeout = TimeSpan.FromSeconds(18) };
    static readonly string DataDirectory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Checkit");
    static readonly TimeZoneInfo Bremen = TimeZoneInfo.FindSystemTimeZoneById("W. Europe Standard Time");
    readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromSeconds(60) };
    readonly DispatcherTimer countdownTimer = new() { Interval = TimeSpan.FromSeconds(1) };
    Stop stop = new("de-DELFI_de:04011:13927_G", "Bremen Hauptbahnhof");
    Snapshot? snapshot;
    bool busy, stale;
    long revision;
    public MainWindow() {
        InitializeComponent();
        try {
            var preferences = JsonSerializer.Deserialize<Preferences>(File.ReadAllText(Path.Combine(DataDirectory,"settings.json")));
            if(preferences is not null) { stop=preferences.Stop;Topmost=preferences.Pinned;Width=Math.Clamp(preferences.Width,300,1200);Height=Math.Clamp(preferences.Height,210,1000);Left=Math.Clamp(preferences.Left,SystemParameters.VirtualScreenLeft,Math.Max(SystemParameters.VirtualScreenLeft,SystemParameters.VirtualScreenLeft+SystemParameters.VirtualScreenWidth-Width));Top=Math.Clamp(preferences.Top,SystemParameters.VirtualScreenTop,Math.Max(SystemParameters.VirtualScreenTop,SystemParameters.VirtualScreenTop+SystemParameters.VirtualScreenHeight-Height)); }
            snapshot = JsonSerializer.Deserialize<Snapshot>(File.ReadAllText(Path.Combine(DataDirectory,"snapshot.json")));
            if(snapshot?.station.id!=stop.id)snapshot=null;
            stale=true;
        } catch { }
        timer.Tick += async (_,_)=> await Refresh();
        countdownTimer.Tick += (_,_)=> Render();
    }
    async void OnLoaded(object sender,RoutedEventArgs e) { Pin.Content=Topmost?"◆":"◇";Render();timer.Start();countdownTimer.Start();await Refresh(); }
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
    static string Clock(string? value)=>DateTimeOffset.TryParse(value,out var parsed)?TimeZoneInfo.ConvertTime(parsed,Bremen).ToString("HH:mm"):"—";
    static string Remaining(string? value) => DateTimeOffset.TryParse(value,out var parsed) ? (parsed <= DateTimeOffset.UtcNow ? "jetzt" : $"{Math.Ceiling((parsed-DateTimeOffset.UtcNow).TotalMinutes)} min") : "—";
    static Brush Color(string? value, string fallback) { try { return (Brush)new BrushConverter().ConvertFromString(value??fallback)!; }catch{return (Brush)new BrushConverter().ConvertFromString(fallback)!;} }
    void Render() {
        if(Rows is null)return;
        Station.Text=stop.name;Rows.Children.Clear();
        var aged=snapshot is not null&&(DateTimeOffset.UtcNow-snapshot.generatedAt).TotalMinutes>2;
        Status.Text=snapshot is null?"OFFLINE · Bitte aktualisieren":$"{(stale||aged?"VERALTET":snapshot.departures.Any(d=>d.realtime)?"ECHTZEIT":"FAHRPLAN")} · Stand {TimeZoneInfo.ConvertTime(snapshot.generatedAt,Bremen):HH:mm}";
        var count=Math.Clamp((int)(Rows.ActualHeight/42),0,12);
        var items=snapshot?.departures.Where(d=>DateTimeOffset.TryParse(d.time.realtime??d.time.scheduled,out var date)&&date>DateTimeOffset.UtcNow.AddMinutes(-1)).Take(count).ToList();
        if(items is null||items.Count==0){if(count>0)Rows.Children.Add(new TextBlock{Text=snapshot is null?"Verbindung prüfen und ↻ drücken.":"Keine aktuellen Abfahrten.",FontSize=12,Foreground=Color(null,"#A8ADB5"),Margin=new Thickness(0,8,0,0)});return;}
        foreach(var d in items) {
            var row=new Grid{Height=42};row.ColumnDefinitions.Add(new(){Width=new GridLength(43)});row.ColumnDefinitions.Add(new(){Width=new GridLength(1,GridUnitType.Star)});row.ColumnDefinitions.Add(new(){Width=new GridLength(68)});
            var badge=new Border{Background=Color(d.line.color,"#FFB51B"),CornerRadius=new CornerRadius(2),Padding=new Thickness(3),VerticalAlignment=VerticalAlignment.Center,Margin=new Thickness(0,0,7,0),Child=new TextBlock{Text=d.line.label,Foreground=Color(d.line.textColor,"#111317"),FontWeight=FontWeights.Bold,FontSize=12,TextAlignment=TextAlignment.Center,TextTrimming=TextTrimming.CharacterEllipsis}};
            row.Children.Add(badge);
            var destination=new StackPanel{VerticalAlignment=VerticalAlignment.Center,Margin=new Thickness(3,0,8,0)};
            destination.Children.Add(new TextBlock{Text=d.destination,TextTrimming=TextTrimming.CharacterEllipsis,FontSize=14,ToolTip=d.destination});
            if(d.time.delayMinutes>0)destination.Children.Add(new TextBlock{Text=$"+{d.time.delayMinutes} min",FontSize=9,Foreground=Color(null,"#FFB51B")});
            Grid.SetColumn(destination,1);row.Children.Add(destination);
            var time=new StackPanel{VerticalAlignment=VerticalAlignment.Center};
            time.Children.Add(new TextBlock{Text=d.cancelled?"AUS":Remaining(d.time.realtime??d.time.scheduled),Foreground=Color(null,"#FFB51B"),FontSize=17,FontWeight=FontWeights.SemiBold,TextAlignment=TextAlignment.Right});
            time.Children.Add(new TextBlock{Text=Clock(d.time.realtime??d.time.scheduled),Foreground=Color(null,"#A8ADB5"),FontSize=9,TextAlignment=TextAlignment.Right});
            Grid.SetColumn(time,2);row.Children.Add(time);Rows.Children.Add(row);
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
    void QueryKeyDown(object sender,KeyEventArgs e){if(e.Key==Key.Enter&&SearchButton.IsEnabled)Search(sender,e);}
    void ToggleSettings(object sender,RoutedEventArgs e){Settings.Visibility=Settings.Visibility==Visibility.Visible?Visibility.Collapsed:Visibility.Visible;if(Settings.Visibility==Visibility.Visible){Height=Math.Max(Height,460);Query.Focus();}Dispatcher.BeginInvoke(Render,DispatcherPriority.Loaded);}
    void TogglePin(object sender,RoutedEventArgs e){Topmost=!Topmost;Pin.Content=Topmost?"◆":"◇";SavePreferences();}
    void DragHeader(object sender,MouseButtonEventArgs e){if(e.OriginalSource is TextBlock)DragMove();}
    void Minimize(object sender,RoutedEventArgs e)=>WindowState=WindowState.Minimized;
    void CloseWindow(object sender,RoutedEventArgs e)=>Close();
    async void ManualRefresh(object sender,RoutedEventArgs e)=>await Refresh();
    void OnSizeChanged(object sender,SizeChangedEventArgs e)=>Dispatcher.BeginInvoke(Render,DispatcherPriority.Loaded);
    void SavePreferences(){try{Directory.CreateDirectory(DataDirectory);File.WriteAllText(Path.Combine(DataDirectory,"settings.json"),JsonSerializer.Serialize(new Preferences(stop,Topmost,Width,Height,Left,Top)));}catch{}}
    void OnClosing(object? sender,System.ComponentModel.CancelEventArgs e){timer.Stop();countdownTimer.Stop();SavePreferences();}
    void OpenLink(object sender,System.Windows.Navigation.RequestNavigateEventArgs e){Process.Start(new ProcessStartInfo(e.Uri.AbsoluteUri){UseShellExecute=true});e.Handled=true;}
}
