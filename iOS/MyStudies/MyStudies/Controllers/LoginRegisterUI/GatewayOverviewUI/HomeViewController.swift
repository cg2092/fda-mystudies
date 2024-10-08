// License Agreement for FDA MyStudies
// Copyright © 2017-2019 Harvard Pilgrim Health Care Institute (HPHCI) and its Contributors.
// Copyright 2020 Google LLC
// Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
// documentation files (the &quot;Software&quot;), to deal in the Software without restriction, including without
// limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
// Software, and to permit persons to whom the Software is furnished to do so, subject to the following
// conditions:
// The above copyright notice and this permission notice shall be included in all copies or substantial
// portions of the Software.
// Funding Source: Food and Drug Administration (“Funding Agency”) effective 18 September 2014 as
// Contract no. HHSF22320140030I/HHSF22301006T (the “Prime Contract”).
// THE SOFTWARE IS PROVIDED &quot;AS IS&quot;, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
// INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
// PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT
// OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
// OTHER DEALINGS IN THE SOFTWARE.

import Foundation
import SlideMenuControllerSwift
import UIKit
import FirebaseAnalytics
import Reachability

class HomeViewController: UIViewController {

  // MARK: - Outlets
  @IBOutlet weak var container: UIView!

  @IBOutlet var pageControlView: UIPageControl?
  @IBOutlet var buttonLink: UIButton!
  @IBOutlet var buttonSignin: UIButton!
  @IBOutlet var buttonRegister: UIButton!
  @IBOutlet var buttonGetStarted: UIButton?

  var websiteName: String!
  private var reachability: Reachability!

  // MARK: - ViewController Lifecycle

  override func loadView() {
    super.loadView()
    self.loadGatewayUI()
  }

  override func viewDidLoad() {
    super.viewDidLoad()

      setupNotifiers()
    /// Added to change next screen
    pageControlView?.addTarget(
      self,
      action: #selector(HomeViewController.didChangePageControlValue),
      for: .valueChanged
    )

    websiteName = Branding.websiteLink
    let title = Branding.websiteButtonTitle

    buttonLink.setTitle(title, for: .normal)
  }
  
  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)

    // hide navigationbar
    self.navigationController?.setNavigationBarHidden(true, animated: true)

  }
  
  // MARK: - Utility functions
  func setupNotifiers() {
        NotificationCenter.default.addObserver(self, selector:#selector(reachabilityChanged(note:)),
                                               name: Notification.Name.reachabilityChanged, object: nil);
        
        do {
            self.reachability = try Reachability()
            try self.reachability.startNotifier()
            } catch(let error) { }
  }
    
  @objc func reachabilityChanged(note: Notification) {
      let reachability = note.object as! Reachability
      switch reachability.connection {
      case .cellular:
          setOnline()
          break
      case .wifi:
          setOnline()
          break
      case .none:
          setOffline()
          break
      case .unavailable:
          setOffline()
            break
      }
    }
  
    func setOnline() {
        self.view.hideAllToasts()
        buttonSignin.isEnabled = true
        buttonSignin.layer.opacity = 1
        buttonLink.isEnabled = true
        buttonLink.layer.opacity = 1
    }
  
    func setOffline() {
        self.view.makeToast("You are offline", duration: Double.greatestFiniteMagnitude, position: .top, title: nil, image: nil, completion: nil)
        buttonSignin.isEnabled = false
        buttonSignin.layer.opacity = 0.5
        buttonLink.isEnabled = false
        buttonLink.layer.opacity = 0.5
    }
  
    override func showOfflineIndicator() -> Bool {
        return true
    }

  // MARK: - UI Utils

  /// To load Initial Gateway data setup.
  func loadGatewayUI() {

    // load plist info
    guard
      let plistPath = Bundle.main.path(
        forResource: "GatewayOverview",
        ofType: ".plist",
        inDirectory: nil
      ),
      let arrayContent = NSMutableArray(contentsOfFile: plistPath)
    else { return }

    var listOfOverviews: [OverviewSection] = []

    arrayContent.forEach { (overview) in
      let overviewObj = OverviewSection(detail: overview as! [String: Any])
      listOfOverviews.append(overviewObj)
    }

    // create new Overview object
    let overview = Overview()
    overview.type = .gateway
    overview.sections = listOfOverviews

    // Assign to Gateway
    Gateway.instance.overview = overview

    if overview.sections.count <= 1 {
      self.pageControlView?.isHidden = true
    }
  }

  // MARK: - Segue Methods

  override func prepare(for segue: UIStoryboardSegue, sender: Any?) {

    if let pageViewController = segue.destination as? PageViewController {
      pageViewController.pageViewDelegate = self
      pageViewController.overview = Gateway.instance.overview!
    }

    if let signInController = segue.destination as? SignInViewController {
      signInController.viewLoadFrom = .gatewayOverview
    }

    if let signUpController = segue.destination as? SignUpViewController {
      signUpController.viewLoadFrom = .gatewayOverview
    }

  }

  /// This method is triggered when the user taps on the pageControl to
  /// change its current page (Commented as this is not working).
  @objc func didChangePageControlValue() {
    //pageViewController?.scrollToViewController(index: (pageControlView?.currentPage)!)
  }

  // MARK: - Button Actions

  /// Calls menu view.
  @IBAction func getStartedButtonClicked(_ sender: UIButton) {

    Analytics.logEvent(analyticsButtonClickEventsName, parameters: [
      buttonClickReasonsKey: "Get Started"
    ])
    
    self.createMenuView()
  }

  /// To Create a menu view .
  func createMenuView() {

    let storyboard = UIStoryboard(name: kStoryboardIdentifierGateway, bundle: nil)
    let fdaSlideVC =
      storyboard.instantiateViewController(
        withIdentifier: kStoryboardIdentifierSlideMenuVC
      )
      as! FDASlideMenuViewController

    guard let window = UIApplication.shared.keyWindow else { return }
    guard let rootViewController = window.rootViewController else { return }

    fdaSlideVC.view.frame = rootViewController.view.frame
    fdaSlideVC.view.layoutIfNeeded()

    UIView.transition(
      with: window,
      duration: 0.5,
      options: .transitionCrossDissolve,
      animations: { window.rootViewController = fdaSlideVC },
      completion: { _ in  // maybe do something here
      }
    )
  }

  /// To initialize WebViewController using
  /// Main storyboard.
  @IBAction func linkButtonAction(_ sender: Any) {
    Analytics.logEvent(analyticsButtonClickEventsName, parameters: [
      buttonClickReasonsKey: "Open Website"
    ])
    
    guard let websiteLink = URL(string: Branding.websiteLink) else { return }
    let loginStoryboard = UIStoryboard.init(name: "Main", bundle: Bundle.main)
    let webViewController =
      loginStoryboard.instantiateViewController(
        withIdentifier: "WebViewController"
      ) as! UINavigationController
    let webView = webViewController.viewControllers[0] as! WebViewController
    webView.requestLink = websiteLink.absoluteString
    
    self.navigationController?.present(webViewController, animated: true, completion: nil)
  }

  /// To perform unwind operation after logout.
  @IBAction func unwindForLogout(_ segue: UIStoryboardSegue) {

  }

  /// To Unwind segue for register.
  @IBAction func unwindForRegister(_ segue: UIStoryboardSegue) {
    DispatchQueue.main.asyncAfter(deadline: .now()) {
      self.buttonRegister.sendActions(for: .touchUpInside)
    }
  }

  /// To navigate back to Signin.
  /// - Parameter segue: The segue which is connected to 1 controller to another.
  @IBAction func unwindForSignIn(_ segue: UIStoryboardSegue) {
    DispatchQueue.main.asyncAfter(deadline: .now()) {
      self.buttonSignin.sendActions(for: .touchUpInside)
    }
  }
}

// MARK: - Page Control Delegates for handling Counts
extension HomeViewController: PageViewControllerDelegate {

  func pageViewController(pageViewController: PageViewController, didUpdatePageCount count: Int) {
    pageControlView?.numberOfPages = count
  }

  func pageViewController(pageViewController: PageViewController, didUpdatePageIndex index: Int) {
    pageControlView?.currentPage = index

    buttonGetStarted?.layer.borderColor = kUicolorForButtonBackground

    if index == 0 {
      // For first Page
      UIView.animate(
        withDuration: 0.1,
        animations: {
          self.buttonGetStarted?.backgroundColor = kUIColorForSubmitButtonBackground
          self.buttonGetStarted?.setTitleColor(UIColor.white, for: .normal)
        }
      )
    } else {

      UIView.animate(
        withDuration: 0.1,
        animations: {
          // For All other pages
          self.buttonGetStarted?.backgroundColor = UIColor.white
          self.buttonGetStarted?.setTitleColor(
            kUIColorForSubmitButtonBackground,
            for: .normal
          )
        }
      )
    }
  }
}

extension HomeViewController {
  /// To set HomeViewController as Root ViewController
  static func setRootView() {
    
    let storyboard = UIStoryboard(name: kStoryboardIdentifierLogin, bundle: nil)
    let homeVC =
      storyboard.instantiateViewController(
        withIdentifier: kStoryboardIdentifierHomeView
      ) as! HomeViewController
    
    let navC = UINavigationController.init(rootViewController: homeVC)
    navC.navigationBar.isHidden = true
    guard let window = UIApplication.shared.keyWindow else { return }
    guard let rootViewController = window.rootViewController else { return }
    
    homeVC.view.frame = rootViewController.view.frame
    homeVC.view.layoutIfNeeded()
    
    UIView.transition(
      with: window,
      duration: 0.5,
      options: .transitionCrossDissolve,
      animations: { window.rootViewController = navC },
      completion: { _ in }
    )
  }
}
